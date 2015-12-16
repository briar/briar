package org.briarproject.sync;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ShutdownEvent;
import org.briarproject.api.event.TransportRemovedEvent;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.MessagingSession;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.SubscriptionAck;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.TransportAck;
import org.briarproject.api.sync.TransportUpdate;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.MessagingConstants.MAX_PAYLOAD_LENGTH;

/**
 * An outgoing {@link org.briarproject.api.sync.MessagingSession
 * MessagingSession} suitable for simplex transports. The session sends
 * messages without offering them, and closes its output stream when there are
 * no more packets to send.
 */
class SimplexOutgoingSession implements MessagingSession, EventListener {

	private static final Logger LOG =
			Logger.getLogger(SimplexOutgoingSession.class.getName());

	private static final ThrowingRunnable<IOException> CLOSE =
			new ThrowingRunnable<IOException>() {
		public void run() {}
	};

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final ContactId contactId;
	private final TransportId transportId;
	private final int maxLatency;
	private final PacketWriter packetWriter;
	private final AtomicInteger outstandingQueries;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private volatile boolean interrupted = false;

	SimplexOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, ContactId contactId, TransportId transportId,
			int maxLatency, PacketWriter packetWriter) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.contactId = contactId;
		this.transportId = transportId;
		this.maxLatency = maxLatency;
		this.packetWriter = packetWriter;
		outstandingQueries = new AtomicInteger(6); // One per type of packet
		writerTasks = new LinkedBlockingQueue<ThrowingRunnable<IOException>>();
	}

	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Start a query for each type of packet, in order of urgency
			dbExecutor.execute(new GenerateTransportAcks());
			dbExecutor.execute(new GenerateTransportUpdates());
			dbExecutor.execute(new GenerateSubscriptionAck());
			dbExecutor.execute(new GenerateSubscriptionUpdate());
			dbExecutor.execute(new GenerateAck());
			dbExecutor.execute(new GenerateBatch());
			// Write packets until interrupted or no more packets to write
			try {
				while (!interrupted) {
					ThrowingRunnable<IOException> task = writerTasks.take();
					if (task == CLOSE) break;
					task.run();
				}
				packetWriter.flush();
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

	private void decrementOutstandingQueries() {
		if (outstandingQueries.decrementAndGet() == 0) writerTasks.add(CLOSE);
	}

	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(contactId)) interrupt();
		} else if (e instanceof ShutdownEvent) {
			interrupt();
		} else if (e instanceof TransportRemovedEvent) {
			TransportRemovedEvent t = (TransportRemovedEvent) e;
			if (t.getTransportId().equals(transportId)) interrupt();
		}
	}

	// This task runs on the database thread
	private class GenerateAck implements Runnable {

		public void run() {
			if (interrupted) return;
			int maxMessages = packetWriter.getMaxMessagesForAck(Long.MAX_VALUE);
			try {
				Ack a = db.generateAck(contactId, maxMessages);
				if (LOG.isLoggable(INFO))
					LOG.info("Generated ack: " + (a != null));
				if (a == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteAck(a));
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
				Collection<byte[]> b = db.generateBatch(contactId,
						MAX_PAYLOAD_LENGTH, maxLatency);
				if (LOG.isLoggable(INFO))
					LOG.info("Generated batch: " + (b != null));
				if (b == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteBatch(b));
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
	private class GenerateSubscriptionAck implements Runnable {

		public void run() {
			if (interrupted) return;
			try {
				SubscriptionAck a = db.generateSubscriptionAck(contactId);
				if (LOG.isLoggable(INFO))
					LOG.info("Generated subscription ack: " + (a != null));
				if (a == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteSubscriptionAck(a));
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This tasks runs on the writer thread
	private class WriteSubscriptionAck
	implements ThrowingRunnable<IOException> {

		private final SubscriptionAck ack;

		private WriteSubscriptionAck(SubscriptionAck ack) {
			this.ack = ack;
		}

		public void run() throws IOException {
			if (interrupted) return;
			packetWriter.writeSubscriptionAck(ack);
			LOG.info("Sent subscription ack");
			dbExecutor.execute(new GenerateSubscriptionAck());
		}
	}

	// This task runs on the database thread
	private class GenerateSubscriptionUpdate implements Runnable {

		public void run() {
			if (interrupted) return;
			try {
				SubscriptionUpdate u =
						db.generateSubscriptionUpdate(contactId, maxLatency);
				if (LOG.isLoggable(INFO))
					LOG.info("Generated subscription update: " + (u != null));
				if (u == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteSubscriptionUpdate(u));
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteSubscriptionUpdate
	implements ThrowingRunnable<IOException> {

		private final SubscriptionUpdate update;

		private WriteSubscriptionUpdate(SubscriptionUpdate update) {
			this.update = update;
		}

		public void run() throws IOException {
			if (interrupted) return;
			packetWriter.writeSubscriptionUpdate(update);
			LOG.info("Sent subscription update");
			dbExecutor.execute(new GenerateSubscriptionUpdate());
		}
	}

	// This task runs on the database thread
	private class GenerateTransportAcks implements Runnable {

		public void run() {
			if (interrupted) return;
			try {
				Collection<TransportAck> acks =
						db.generateTransportAcks(contactId);
				if (LOG.isLoggable(INFO))
					LOG.info("Generated transport acks: " + (acks != null));
				if (acks == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteTransportAcks(acks));
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This tasks runs on the writer thread
	private class WriteTransportAcks implements ThrowingRunnable<IOException> {

		private final Collection<TransportAck> acks;

		private WriteTransportAcks(Collection<TransportAck> acks) {
			this.acks = acks;
		}

		public void run() throws IOException {
			if (interrupted) return;
			for (TransportAck a : acks) packetWriter.writeTransportAck(a);
			LOG.info("Sent transport acks");
			dbExecutor.execute(new GenerateTransportAcks());
		}
	}

	// This task runs on the database thread
	private class GenerateTransportUpdates implements Runnable {

		public void run() {
			if (interrupted) return;
			try {
				Collection<TransportUpdate> t =
						db.generateTransportUpdates(contactId, maxLatency);
				if (LOG.isLoggable(INFO))
					LOG.info("Generated transport updates: " + (t != null));
				if (t == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteTransportUpdates(t));
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				interrupt();
			}
		}
	}

	// This task runs on the writer thread
	private class WriteTransportUpdates
	implements ThrowingRunnable<IOException> {

		private final Collection<TransportUpdate> updates;

		private WriteTransportUpdates(Collection<TransportUpdate> updates) {
			this.updates = updates;
		}

		public void run() throws IOException {
			if (interrupted) return;
			for (TransportUpdate u : updates)
				packetWriter.writeTransportUpdate(u);
			LOG.info("Sent transport updates");
			dbExecutor.execute(new GenerateTransportUpdates());
		}
	}
}
