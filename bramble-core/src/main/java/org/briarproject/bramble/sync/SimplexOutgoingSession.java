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
import org.briarproject.bramble.api.sync.RecordWriter;
import org.briarproject.bramble.api.sync.SyncSession;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_RECORD_PAYLOAD_LENGTH;

/**
 * An outgoing {@link SyncSession} suitable for simplex transports. The session
 * sends messages without offering them first, and closes its output stream
 * when there are no more records to send.
 */
@ThreadSafe
@NotNullByDefault
class SimplexOutgoingSession implements SyncSession, EventListener {

	private static final Logger LOG =
			Logger.getLogger(SimplexOutgoingSession.class.getName());

	private static final ThrowingRunnable<IOException> CLOSE =
			new ThrowingRunnable<IOException>() {
				@Override
				public void run() {
				}
			};

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final ContactId contactId;
	private final int maxLatency;
	private final RecordWriter recordWriter;
	private final AtomicInteger outstandingQueries;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private volatile boolean interrupted = false;

	SimplexOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, ContactId contactId,
			int maxLatency, RecordWriter recordWriter) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.contactId = contactId;
		this.maxLatency = maxLatency;
		this.recordWriter = recordWriter;
		outstandingQueries = new AtomicInteger(2); // One per type of record
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
			// Write records until interrupted or no more records to write
			try {
				while (!interrupted) {
					ThrowingRunnable<IOException> task = writerTasks.take();
					if (task == CLOSE) break;
					task.run();
				}
				recordWriter.flush();
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

	private void decrementOutstandingQueries() {
		if (outstandingQueries.decrementAndGet() == 0) writerTasks.add(CLOSE);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(contactId)) interrupt();
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
				if (a == null) decrementOutstandingQueries();
				else writerTasks.add(new WriteAck(a));
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
					b = db.generateBatch(txn, contactId,
							MAX_RECORD_PAYLOAD_LENGTH, maxLatency);
					db.commitTransaction(txn);
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
}
