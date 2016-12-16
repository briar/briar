package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.event.ShutdownEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.Offer;
import org.briarproject.bramble.api.sync.RecordWriter;
import org.briarproject.bramble.api.sync.Request;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.event.GroupVisibilityUpdatedEvent;
import org.briarproject.bramble.api.sync.event.MessageRequestedEvent;
import org.briarproject.bramble.api.sync.event.MessageSharedEvent;
import org.briarproject.bramble.api.sync.event.MessageToAckEvent;
import org.briarproject.bramble.api.sync.event.MessageToRequestEvent;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_RECORD_PAYLOAD_LENGTH;

/**
 * An outgoing {@link SyncSession} suitable for duplex transports. The session
 * offers messages before sending them, keeps its output stream open when there
 * are no records to send, and reacts to events that make records available to
 * send.
 */
@ThreadSafe
@NotNullByDefault
class DuplexOutgoingSession implements SyncSession, EventListener {

	// Check for retransmittable records once every 60 seconds
	private static final int RETX_QUERY_INTERVAL = 60 * 1000;
	private static final Logger LOG =
			Logger.getLogger(DuplexOutgoingSession.class.getName());

	private static final ThrowingRunnable<IOException> CLOSE =
			new ThrowingRunnable<IOException>() {
				@Override
				public void run() {
				}
			};

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final Clock clock;
	private final ContactId contactId;
	private final int maxLatency, maxIdleTime;
	private final RecordWriter recordWriter;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private volatile boolean interrupted = false;

	DuplexOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, Clock clock, ContactId contactId, int maxLatency,
			int maxIdleTime, RecordWriter recordWriter) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.clock = clock;
		this.contactId = contactId;
		this.maxLatency = maxLatency;
		this.maxIdleTime = maxIdleTime;
		this.recordWriter = recordWriter;
		writerTasks = new LinkedBlockingQueue<ThrowingRunnable<IOException>>();
	}

	@IoExecutor
	@Override
	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Start a query for each type of record
			dbExecutor.execute(new GenerateAck());
			dbExecutor.execute(new GenerateBatch());
			dbExecutor.execute(new GenerateOffer());
			dbExecutor.execute(new GenerateRequest());
			long now = clock.currentTimeMillis();
			long nextKeepalive = now + maxIdleTime;
			long nextRetxQuery = now + RETX_QUERY_INTERVAL;
			boolean dataToFlush = true;
			// Write records until interrupted
			try {
				while (!interrupted) {
					// Work out how long we should wait for a record
					now = clock.currentTimeMillis();
					long wait = Math.min(nextKeepalive, nextRetxQuery) - now;
					if (wait < 0) wait = 0;
					// Flush any unflushed data if we're going to wait
					if (wait > 0 && dataToFlush && writerTasks.isEmpty()) {
						recordWriter.flush();
						dataToFlush = false;
						nextKeepalive = now + maxIdleTime;
					}
					// Wait for a record
					ThrowingRunnable<IOException> task = writerTasks.poll(wait,
							MILLISECONDS);
					if (task == null) {
						now = clock.currentTimeMillis();
						if (now >= nextRetxQuery) {
							// Check for retransmittable records
							dbExecutor.execute(new GenerateBatch());
							dbExecutor.execute(new GenerateOffer());
							nextRetxQuery = now + RETX_QUERY_INTERVAL;
						}
						if (now >= nextKeepalive) {
							// Flush the stream to keep it alive
							recordWriter.flush();
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
				if (dataToFlush) recordWriter.flush();
			} catch (InterruptedException e) {
				LOG.info("Interrupted while waiting for a record to write");
				Thread.currentThread().interrupt();
			}
		} finally {
			eventBus.removeListener(this);
		}
	}

	@Override
	public void interrupt() {
		interrupted = true;
		writerTasks.add(CLOSE);
	}

	@Override
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

	private class GenerateAck implements Runnable {

		@DatabaseExecutor
		@Override
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

	private class WriteAck implements ThrowingRunnable<IOException> {

		private final Ack ack;

		private WriteAck(Ack ack) {
			this.ack = ack;
		}

		@IoExecutor
		@Override
		public void run() throws IOException {
			if (interrupted) return;
			recordWriter.writeAck(ack);
			LOG.info("Sent ack");
			dbExecutor.execute(new GenerateAck());
		}
	}

	private class GenerateBatch implements Runnable {

		@DatabaseExecutor
		@Override
		public void run() {
			if (interrupted) return;
			try {
				Collection<byte[]> b;
				Transaction txn = db.startTransaction(false);
				try {
					b = db.generateRequestedBatch(txn, contactId,
							MAX_RECORD_PAYLOAD_LENGTH, maxLatency);
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

	private class WriteBatch implements ThrowingRunnable<IOException> {

		private final Collection<byte[]> batch;

		private WriteBatch(Collection<byte[]> batch) {
			this.batch = batch;
		}

		@IoExecutor
		@Override
		public void run() throws IOException {
			if (interrupted) return;
			for (byte[] raw : batch) recordWriter.writeMessage(raw);
			LOG.info("Sent batch");
			dbExecutor.execute(new GenerateBatch());
		}
	}

	private class GenerateOffer implements Runnable {

		@DatabaseExecutor
		@Override
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

	private class WriteOffer implements ThrowingRunnable<IOException> {

		private final Offer offer;

		private WriteOffer(Offer offer) {
			this.offer = offer;
		}

		@IoExecutor
		@Override
		public void run() throws IOException {
			if (interrupted) return;
			recordWriter.writeOffer(offer);
			LOG.info("Sent offer");
			dbExecutor.execute(new GenerateOffer());
		}
	}

	private class GenerateRequest implements Runnable {

		@DatabaseExecutor
		@Override
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

	private class WriteRequest implements ThrowingRunnable<IOException> {

		private final Request request;

		private WriteRequest(Request request) {
			this.request = request;
		}

		@IoExecutor
		@Override
		public void run() throws IOException {
			if (interrupted) return;
			recordWriter.writeRequest(request);
			LOG.info("Sent request");
			dbExecutor.execute(new GenerateRequest());
		}
	}
}
