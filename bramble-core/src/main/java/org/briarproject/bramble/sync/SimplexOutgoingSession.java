package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.event.LifecycleEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.TransportInactiveEvent;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.Versions;
import org.briarproject.bramble.api.sync.event.CloseSyncConnectionsEvent;
import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STOPPING;
import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.api.sync.SyncConstants.SUPPORTED_VERSIONS;
import static org.briarproject.bramble.util.LogUtils.logException;

/**
 * An outgoing {@link SyncSession} suitable for simplex transports. The session
 * sends messages without offering them first, and closes its output stream
 * when there are no more records to send.
 */
@ThreadSafe
@NotNullByDefault
class SimplexOutgoingSession implements SyncSession, EventListener {

	private static final Logger LOG =
			getLogger(SimplexOutgoingSession.class.getName());

	private static final ThrowingRunnable<IOException> CLOSE = () -> {
	};

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final ContactId contactId;
	private final TransportId transportId;
	private final int maxLatency;
	private final boolean eager;
	private final StreamWriter streamWriter;
	private final SyncRecordWriter recordWriter;
	private final AtomicInteger outstandingQueries;
	private final BlockingQueue<ThrowingRunnable<IOException>> writerTasks;

	private volatile boolean interrupted = false;

	SimplexOutgoingSession(DatabaseComponent db, Executor dbExecutor,
			EventBus eventBus, ContactId contactId, TransportId transportId,
			int maxLatency, boolean eager, StreamWriter streamWriter,
			SyncRecordWriter recordWriter) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.eventBus = eventBus;
		this.contactId = contactId;
		this.transportId = transportId;
		this.maxLatency = maxLatency;
		this.eager = eager;
		this.streamWriter = streamWriter;
		this.recordWriter = recordWriter;
		outstandingQueries = new AtomicInteger(2); // One per type of record
		writerTasks = new LinkedBlockingQueue<>();
	}

	@IoExecutor
	@Override
	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Send our supported protocol versions
			recordWriter.writeVersions(new Versions(SUPPORTED_VERSIONS));
			// Start a query for each type of record
			dbExecutor.execute(this::generateAck);
			if (eager) dbExecutor.execute(this::loadUnackedMessageIds);
			else dbExecutor.execute(this::generateBatch);
			// Write records until interrupted or no more records to write
			try {
				while (!interrupted) {
					ThrowingRunnable<IOException> task = writerTasks.take();
					if (task == CLOSE) break;
					task.run();
				}
				streamWriter.sendEndOfStream();
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
		} else if (e instanceof LifecycleEvent) {
			LifecycleEvent l = (LifecycleEvent) e;
			if (l.getLifecycleState() == STOPPING) interrupt();
		} else if (e instanceof CloseSyncConnectionsEvent) {
			CloseSyncConnectionsEvent c = (CloseSyncConnectionsEvent) e;
			if (c.getTransportId().equals(transportId)) interrupt();
		} else if (e instanceof TransportInactiveEvent) {
			TransportInactiveEvent t = (TransportInactiveEvent) e;
			if (t.getTransportId().equals(transportId)) interrupt();
		}
	}

	@DatabaseExecutor
	private void loadUnackedMessageIds() {
		if (interrupted) return;
		try {
			Map<MessageId, Integer> ids = db.transactionWithResult(true, txn ->
					db.getUnackedMessagesToSend(txn, contactId));
			if (LOG.isLoggable(INFO)) {
				LOG.info(ids.size() + " unacked messages to send");
			}
			if (ids.isEmpty()) decrementOutstandingQueries();
			else dbExecutor.execute(() -> generateEagerBatch(ids));
		} catch (DbException e) {
			logException(LOG, WARNING, e);
			interrupt();
		}
	}

	@DatabaseExecutor
	private void generateEagerBatch(Map<MessageId, Integer> ids) {
		if (interrupted) return;
		// Take some message IDs from `ids` to form a batch
		Collection<MessageId> batchIds = new ArrayList<>();
		long totalLength = 0;
		Iterator<Entry<MessageId, Integer>> it = ids.entrySet().iterator();
		while (it.hasNext()) {
			// Check whether the next message will fit in the batch
			Entry<MessageId, Integer> e = it.next();
			int length = e.getValue();
			if (totalLength + length > MAX_RECORD_PAYLOAD_BYTES) break;
			// Add the message to the batch
			it.remove();
			batchIds.add(e.getKey());
			totalLength += length;
		}
		if (batchIds.isEmpty()) throw new AssertionError();
		try {
			Collection<Message> batch =
					db.transactionWithResult(false, txn ->
							db.generateBatch(txn, contactId, batchIds,
									maxLatency));
			writerTasks.add(() -> writeEagerBatch(batch, ids));
		} catch (DbException e) {
			logException(LOG, WARNING, e);
			interrupt();
		}
	}

	@IoExecutor
	private void writeEagerBatch(Collection<Message> batch,
			Map<MessageId, Integer> ids) throws IOException {
		if (interrupted) return;
		for (Message m : batch) recordWriter.writeMessage(m);
		LOG.info("Sent eager batch");
		if (ids.isEmpty()) decrementOutstandingQueries();
		else dbExecutor.execute(() -> generateEagerBatch(ids));
	}

	@DatabaseExecutor
	private void generateAck() {
		if (interrupted) return;
		try {
			Ack a = db.transactionWithNullableResult(false, txn ->
					db.generateAck(txn, contactId, MAX_MESSAGE_IDS));
			if (LOG.isLoggable(INFO))
				LOG.info("Generated ack: " + (a != null));
			if (a == null) decrementOutstandingQueries();
			else writerTasks.add(() -> writeAck(a));
		} catch (DbException e) {
			logException(LOG, WARNING, e);
			interrupt();
		}
	}

	@IoExecutor
	private void writeAck(Ack ack) throws IOException {
		if (interrupted) return;
		recordWriter.writeAck(ack);
		LOG.info("Sent ack");
		dbExecutor.execute(this::generateAck);
	}

	@DatabaseExecutor
	private void generateBatch() {
		if (interrupted) return;
		try {
			Collection<Message> b =
					db.transactionWithNullableResult(false, txn ->
							db.generateBatch(txn, contactId,
									MAX_RECORD_PAYLOAD_BYTES, maxLatency));
			if (LOG.isLoggable(INFO))
				LOG.info("Generated batch: " + (b != null));
			if (b == null) decrementOutstandingQueries();
			else writerTasks.add(() -> writeBatch(b));
		} catch (DbException e) {
			logException(LOG, WARNING, e);
			interrupt();
		}
	}

	@IoExecutor
	private void writeBatch(Collection<Message> batch) throws IOException {
		if (interrupted) return;
		for (Message m : batch) recordWriter.writeMessage(m);
		LOG.info("Sent batch");
		dbExecutor.execute(this::generateBatch);
	}
}
