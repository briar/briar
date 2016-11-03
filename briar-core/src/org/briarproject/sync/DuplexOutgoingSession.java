package org.briarproject.sync;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupVisibilityUpdatedEvent;
import org.briarproject.api.event.MessageRequestedEvent;
import org.briarproject.api.event.MessageSharedEvent;
import org.briarproject.api.event.MessageToAckEvent;
import org.briarproject.api.event.MessageToRequestEvent;
import org.briarproject.api.event.ShutdownEvent;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.system.Clock;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.api.sync.SyncConstants.MAX_PACKET_PAYLOAD_LENGTH;

/**
 * An outgoing {@link org.briarproject.api.sync.SyncSession SyncSession}
 * suitable for duplex transports. The session offers messages before sending
 * them, keeps its output stream open when there are no packets to send, and
 * reacts to events that make packets available to send.
 */
class DuplexOutgoingSession implements SyncSession, EventListener {

	// Check for retransmittable packets once every 60 seconds
	private static final int RETX_QUERY_INTERVAL = 60 * 1000;
	private static final Logger LOG =
			Logger.getLogger(DuplexOutgoingSession.class.getName());

	private static final ThrowingRunnable<IOException> CLOSE =
			new ThrowingRunnable<IOException>() {
				public void run() {}
			};

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final Clock clock;
	private final ContactId contactId;
	private final int maxLatency, maxIdleTime;
	private final PacketWriter packetWriter;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private volatile boolean interrupted = false;

	DuplexOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, Clock clock, ContactId contactId, int maxLatency,
			int maxIdleTime, PacketWriter packetWriter) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.clock = clock;
		this.contactId = contactId;
		this.maxLatency = maxLatency;
		this.maxIdleTime = maxIdleTime;
		this.packetWriter = packetWriter;
		writerTasks = new LinkedBlockingQueue<ThrowingRunnable<IOException>>();
	}

	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Start a query for each type of packet
			dbExecutor.execute(new GenerateAck());
			dbExecutor.execute(new GenerateBatch());
			dbExecutor.execute(new GenerateOffer());
			dbExecutor.execute(new GenerateRequest());
			long now = clock.currentTimeMillis();
			long nextKeepalive = now + maxIdleTime;
			long nextRetxQuery = now + RETX_QUERY_INTERVAL;
			boolean dataToFlush = true;
			// Write packets until interrupted
			try {
				while (!interrupted) {
					// Work out how long we should wait for a packet
					now = clock.currentTimeMillis();
					long wait = Math.min(nextKeepalive, nextRetxQuery) - now;
					if (wait < 0) wait = 0;
					// Flush any unflushed data if we're going to wait
					if (wait > 0 && dataToFlush && writerTasks.isEmpty()) {
						packetWriter.flush();
						dataToFlush = false;
						nextKeepalive = now + maxIdleTime;
					}
					// Wait for a packet
					ThrowingRunnable<IOException> task = writerTasks.poll(wait,
							MILLISECONDS);
					if (task == null) {
						now = clock.currentTimeMillis();
						if (now >= nextRetxQuery) {
							// Check for retransmittable packets
							dbExecutor.execute(new GenerateBatch());
							dbExecutor.execute(new GenerateOffer());
							nextRetxQuery = now + RETX_QUERY_INTERVAL;
						}
						if (now >= nextKeepalive) {
							// Flush the stream to keep it alive
							packetWriter.flush();
							dataToFlush = false;
							nextKeepalive = now + maxIdleTime;
						}
					} else if (task == CLOSE) {
						break;
					} else {
						task.run();
						dataToFlush = true;
					}
				}
				if (dataToFlush) packetWriter.flush();
			} catch (InterruptedException e) {
				LOG.info("Interrupted while waiting for a packet to write");
				Thread.currentThread().interrupt();
			}
		} finally {
			eventBus.removeListener(this);
		}
	}

	public void interrupt() {
		interrupted = true;
		writerTasks.add(CLOSE);
	}

	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(contactId)) interrupt();
		} else if (e instanceof MessageSharedEvent) {
			dbExecutor.execute(new GenerateOffer());
		} else if (e instanceof GroupVisibilityUpdatedEvent) {
			GroupVisibilityUpdatedEvent g = (GroupVisibilityUpdatedEvent) e;
			if (g.getAffectedContacts().contains(contactId))
				dbExecutor.execute(new GenerateOffer());
		} else if (e instanceof MessageRequestedEvent) {
			if (((MessageRequestedEvent) e).getContactId().equals(contactId))
				dbExecutor.execute(new GenerateBatch());
		} else if (e instanceof MessageToAckEvent) {
			if (((MessageToAckEvent) e).getContactId().equals(contactId))
				dbExecutor.execute(new GenerateAck());
		} else if (e instanceof MessageToRequestEvent) {
			if (((MessageToRequestEvent) e).getContactId().equals(contactId))
				dbExecutor.execute(new GenerateRequest());
		} else if (e instanceof ShutdownEvent) {
			interrupt();
		}
	}

	// This task runs on the database thread
	private class GenerateAck implements Runnable {

		public void run() {
			if (interrupted) return;
			try {
				Ack a;
				Transaction txn = db.startTransaction(false);
				try {
					a = db.generateAck(txn, contactId, MAX_MESSAGE_IDS);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
				if (LOG.isLoggable(INFO))
					LOG.info("Generated ack: " + (a != null));
				if (a != null) writerTasks.add(new WriteAck(a));
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteAck implements ThrowingRunnable<IOException> {

		private final Ack ack;

		private WriteAck(Ack ack) {
			this.ack = ack;
		}

		public void run() throws IOException {
			if (interrupted) return;
			packetWriter.writeAck(ack);
			LOG.info("Sent ack");
			dbExecutor.execute(new GenerateAck());
		}
	}

	// This task runs on the database thread
	private class GenerateBatch implements Runnable {

		public void run() {
			if (interrupted) return;
			try {
				Collection<byte[]> b;
				Transaction txn = db.startTransaction(false);
				try {
					b = db.generateRequestedBatch(txn, contactId,
							MAX_PACKET_PAYLOAD_LENGTH, maxLatency);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
				if (LOG.isLoggable(INFO))
					LOG.info("Generated batch: " + (b != null));
				if (b != null) writerTasks.add(new WriteBatch(b));
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteBatch implements ThrowingRunnable<IOException> {

		private final Collection<byte[]> batch;

		private WriteBatch(Collection<byte[]> batch) {
			this.batch = batch;
		}

		public void run() throws IOException {
			if (interrupted) return;
			for (byte[] raw : batch) packetWriter.writeMessage(raw);
			LOG.info("Sent batch");
			dbExecutor.execute(new GenerateBatch());
		}
	}

	// This task runs on the database thread
	private class GenerateOffer implements Runnable {

		public void run() {
			if (interrupted) return;
			try {
				Offer o;
				Transaction txn = db.startTransaction(false);
				try {
					o = db.generateOffer(txn, contactId, MAX_MESSAGE_IDS,
							maxLatency);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
				if (LOG.isLoggable(INFO))
					LOG.info("Generated offer: " + (o != null));
				if (o != null) writerTasks.add(new WriteOffer(o));
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteOffer implements ThrowingRunnable<IOException> {

		private final Offer offer;

		private WriteOffer(Offer offer) {
			this.offer = offer;
		}

		public void run() throws IOException {
			if (interrupted) return;
			packetWriter.writeOffer(offer);
			LOG.info("Sent offer");
			dbExecutor.execute(new GenerateOffer());
		}
	}

	// This task runs on the database thread
	private class GenerateRequest implements Runnable {

		public void run() {
			if (interrupted) return;
			try {
				Request r;
				Transaction txn = db.startTransaction(false);
				try {
					r = db.generateRequest(txn, contactId, MAX_MESSAGE_IDS);
					db.commitTransaction(txn);
				} finally {
					db.endTransaction(txn);
				}
				if (LOG.isLoggable(INFO))
					LOG.info("Generated request: " + (r != null));
				if (r != null) writerTasks.add(new WriteRequest(r));
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteRequest implements ThrowingRunnable<IOException> {

		private final Request request;

		private WriteRequest(Request request) {
			this.request = request;
		}

		public void run() throws IOException {
			if (interrupted) return;
			packetWriter.writeRequest(request);
			LOG.info("Sent request");
			dbExecutor.execute(new GenerateRequest());
		}
	}
}
