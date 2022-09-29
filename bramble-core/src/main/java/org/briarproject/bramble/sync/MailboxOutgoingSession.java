package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.OutgoingSessionRecord;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.record.Record.RECORD_HEADER_BYTES;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

/**
 * A {@link SimplexOutgoingSession} for sending and acking messages via a
 * mailbox. The session uses a {@link OutgoingSessionRecord} to record the IDs
 * of the messages sent and acked during the session so that they can be
 * recorded in the DB as sent or acked after the file has been successfully
 * uploaded to the mailbox.
 */
@ThreadSafe
@NotNullByDefault
class MailboxOutgoingSession extends SimplexOutgoingSession {

	private static final Logger LOG =
			getLogger(MailboxOutgoingSession.class.getName());

	private final OutgoingSessionRecord sessionRecord;
	private final long initialCapacity;

	MailboxOutgoingSession(DatabaseComponent db,
			EventBus eventBus,
			ContactId contactId,
			TransportId transportId,
			long maxLatency,
			StreamWriter streamWriter,
			SyncRecordWriter recordWriter,
			OutgoingSessionRecord sessionRecord,
			long capacity) {
		super(db, eventBus, contactId, transportId, maxLatency, streamWriter,
				recordWriter);
		this.sessionRecord = sessionRecord;
		this.initialCapacity = capacity;
	}

	@Override
	void sendAcks() throws DbException, IOException {
		List<MessageId> idsToAck = loadMessageIdsToAck();
		int idsSent = 0;
		while (idsSent < idsToAck.size() && !isInterrupted()) {
			int idsRemaining = idsToAck.size() - idsSent;
			long capacity = getRemainingCapacity();
			long idCapacity =
					(capacity - RECORD_HEADER_BYTES) / MessageId.LENGTH;
			if (idCapacity == 0) break; // Out of capacity
			int idsInRecord = (int) min(idCapacity, MAX_MESSAGE_IDS);
			int idsToSend = min(idsRemaining, idsInRecord);
			List<MessageId> acked =
					idsToAck.subList(idsSent, idsSent + idsToSend);
			recordWriter.writeAck(new Ack(acked));
			sessionRecord.onAckSent(acked);
			LOG.info("Sent ack");
			idsSent += idsToSend;
		}
	}

	private List<MessageId> loadMessageIdsToAck() throws DbException {
		Collection<MessageId> ids = db.transactionWithResult(true, txn ->
				db.getMessagesToAck(txn, contactId));
		if (LOG.isLoggable(INFO)) {
			LOG.info(ids.size() + " messages to ack");
		}
		return new ArrayList<>(ids);
	}

	private long getRemainingCapacity() {
		return initialCapacity - recordWriter.getBytesWritten();
	}

	@Override
	void sendMessages() throws DbException, IOException {
		for (MessageId m : loadMessageIdsToSend()) {
			if (isInterrupted()) break;
			// Defer marking the message as sent
			Message message = db.transactionWithNullableResult(true, txn ->
					db.getMessageToSend(txn, contactId, m, maxLatency, false));
			if (message == null) continue; // No longer shared
			recordWriter.writeMessage(message);
			sessionRecord.onMessageSent(m);
			LOG.info("Sent message");
		}
	}

	private Collection<MessageId> loadMessageIdsToSend() throws DbException {
		long capacity = getRemainingCapacity();
		if (capacity < RECORD_HEADER_BYTES + MESSAGE_HEADER_LENGTH) {
			return emptyList(); // Out of capacity
		}
		Collection<MessageId> ids = db.transactionWithResult(true, txn ->
				db.getMessagesToSend(txn, contactId, capacity, maxLatency));
		if (LOG.isLoggable(INFO)) {
			LOG.info(ids.size() + " messages to send");
		}
		return ids;
	}

}
