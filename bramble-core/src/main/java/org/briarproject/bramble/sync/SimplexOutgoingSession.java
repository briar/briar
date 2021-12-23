package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseComponent;
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
import org.briarproject.bramble.api.sync.SyncSessionId;
import org.briarproject.bramble.api.sync.Versions;
import org.briarproject.bramble.api.sync.event.CloseSyncConnectionsEvent;
import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.annotation.Nullable;
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

	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final ContactId contactId;
	private final TransportId transportId;
	private final long maxLatency;
	private final boolean eager;
	private final StreamWriter streamWriter;
	private final SyncRecordWriter recordWriter;
	@Nullable
	private final SyncSessionId syncSessionId;

	private volatile boolean interrupted = false;

	/**
	 * @param syncSessionId A unique ID for recording the IDs of the messages
	 * sent and acked in this session. The recorded IDs are deleted if the
	 * session completes without throwing an {@link IOException}. If this
	 * parameter is null the IDs will not be recorded.
	 */
	SimplexOutgoingSession(DatabaseComponent db,
			EventBus eventBus,
			ContactId contactId,
			TransportId transportId,
			long maxLatency,
			boolean eager,
			StreamWriter streamWriter,
			SyncRecordWriter recordWriter,
			@Nullable SyncSessionId syncSessionId) {
		this.db = db;
		this.eventBus = eventBus;
		this.contactId = contactId;
		this.transportId = transportId;
		this.maxLatency = maxLatency;
		this.eager = eager;
		this.streamWriter = streamWriter;
		this.recordWriter = recordWriter;
		this.syncSessionId = syncSessionId;
	}

	@IoExecutor
	@Override
	public void run() throws IOException {
		eventBus.addListener(this);
		try {
			// Send our supported protocol versions
			recordWriter.writeVersions(new Versions(SUPPORTED_VERSIONS));
			try {
				// Send any waiting acks
				while (!interrupted) if (!generateAndWriteAck()) break;
				// Send any waiting messages
				if (eager) {
					Map<MessageId, Integer> ids = loadUnackedMessageIds();
					while (!interrupted && !ids.isEmpty()) {
						generateAndWriteEagerBatch(ids);
					}
				} else {
					while (!interrupted) if (!generateAndWriteBatch()) break;
				}
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
			// Send the end of stream marker. If this succeeds we conclude
			// that all messages that were recorded as acked/sent have been
			// written to the transport. If we caught a DbException above then
			// some messages may not have been recorded as acked/sent, but
			// those messages weren't written to the transport either, so the
			// recorded message IDs are still consistent with what was written
			// to the transport
			streamWriter.sendEndOfStream();
			// Now that the output stream has been flushed we can remove
			// the recorded message IDs from the database
			if (syncSessionId != null) setSyncSessionComplete(syncSessionId);
		} catch (IOException e) {
			if (syncSessionId != null) resetSyncSession(syncSessionId);
			throw e;
		} finally {
			eventBus.removeListener(this);
		}
	}

	@Override
	public void interrupt() {
		interrupted = true;
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

	private Map<MessageId, Integer> loadUnackedMessageIds() throws DbException {
		Map<MessageId, Integer> ids = db.transactionWithResult(true, txn ->
				db.getUnackedMessagesToSend(txn, contactId));
		if (LOG.isLoggable(INFO)) {
			LOG.info(ids.size() + " unacked messages to send");
		}
		return ids;
	}

	private void generateAndWriteEagerBatch(Map<MessageId, Integer> ids)
			throws DbException, IOException {
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
		Collection<Message> b = db.transactionWithResult(false, txn -> {
			Collection<Message> batch = db.generateBatch(txn, contactId,
					batchIds, maxLatency);
			if (syncSessionId != null && !batch.isEmpty()) {
				db.addSentMessageIds(txn, contactId, syncSessionId,
						getIds(batch));
			}
			return batch;
		});
		// The batch may be empty if some of the messages are no longer shared
		if (!b.isEmpty()) {
			for (Message m : b) recordWriter.writeMessage(m);
			LOG.info("Sent eager batch");
		}
	}

	private boolean generateAndWriteAck() throws DbException, IOException {
		Ack a = db.transactionWithNullableResult(false, txn -> {
			Ack ack = db.generateAck(txn, contactId, MAX_MESSAGE_IDS);
			if (syncSessionId != null && ack != null) {
				db.addAckedMessageIds(txn, contactId, syncSessionId,
						ack.getMessageIds());
			}
			return ack;
		});
		if (LOG.isLoggable(INFO))
			LOG.info("Generated ack: " + (a != null));
		if (a == null) return false;
		recordWriter.writeAck(a);
		LOG.info("Sent ack");
		return true;
	}

	private boolean generateAndWriteBatch() throws DbException, IOException {
		Collection<Message> b = db.transactionWithNullableResult(false, txn -> {
			Collection<Message> batch = db.generateBatch(txn, contactId,
					MAX_RECORD_PAYLOAD_BYTES, maxLatency);
			if (syncSessionId != null && batch != null) {
				db.addSentMessageIds(txn, contactId, syncSessionId,
						getIds(batch));
			}
			return batch;
		});
		if (LOG.isLoggable(INFO))
			LOG.info("Generated batch: " + (b != null));
		if (b == null) return false;
		for (Message m : b) recordWriter.writeMessage(m);
		LOG.info("Sent batch");
		return true;
	}

	private void setSyncSessionComplete(SyncSessionId syncSessionId) {
		try {
			db.transaction(false, txn ->
					db.setSyncSessionComplete(txn, contactId, syncSessionId));
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void resetSyncSession(SyncSessionId syncSessionId) {
		try {
			db.transaction(false, txn ->
					db.resetIncompleteSyncSession(txn, contactId,
							syncSessionId));
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	private Collection<MessageId> getIds(Collection<Message> batch) {
		Collection<MessageId> ids = new ArrayList<>(batch.size());
		for (Message m : batch) ids.add(m.getId());
		return ids;
	}
}
