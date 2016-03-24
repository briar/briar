package org.briarproject.sync;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ShutdownEvent;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.SyncSession;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.api.sync.SyncConstants.MAX_PACKET_PAYLOAD_LENGTH;

/**
 * An outgoing {@link org.briarproject.api.sync.SyncSession SyncSession}
 * suitable for simplex transports. The session sends messages without offering
 * them first, and closes its output stream when there are no more packets to
 * send.
 */
class SimplexOutgoingSession implements SyncSession, EventListener {

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
	private final int maxLatency;
	private final PacketWriter packetWriter;
	private final AtomicInteger outstandingQueries;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private volatile boolean interrupted = false;

	SimplexOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, ContactId contactId,
			int maxLatency, PacketWriter packetWriter) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.contactId = contactId;
		this.maxLatency = maxLatency;
		this.packetWriter = packetWriter;
		outstandingQueries = new AtomicInteger(2); // One per type of packet
		writerTasks = new LinkedBlockingQueue<ThrowingRunnable<IOException>>();
	}

	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Start a query for each type of packet
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
					txn.setComplete();
				} finally {
					db.endTransaction(txn);
				}
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
				Collection<byte[]> b;
				Transaction txn = db.startTransaction(false);
				try {
					b = db.generateBatch(txn, contactId,
							MAX_PACKET_PAYLOAD_LENGTH, maxLatency);
					txn.setComplete();
				} finally {
					db.endTransaction(txn);
				}
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
}
