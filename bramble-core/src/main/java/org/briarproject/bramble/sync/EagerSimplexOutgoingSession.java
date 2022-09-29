package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

/**
 * A {@link SimplexOutgoingSession} that sends messages eagerly, ie
 * regardless of whether they're due for retransmission.
 */
@ThreadSafe
@NotNullByDefault
class EagerSimplexOutgoingSession extends SimplexOutgoingSession {

	private static final Logger LOG =
			getLogger(EagerSimplexOutgoingSession.class.getName());

	EagerSimplexOutgoingSession(DatabaseComponent db,
			EventBus eventBus,
			ContactId contactId,
			TransportId transportId,
			long maxLatency,
			StreamWriter streamWriter,
			SyncRecordWriter recordWriter) {
		super(db, eventBus, contactId, transportId, maxLatency, streamWriter,
				recordWriter);
	}

	@Override
	void sendMessages() throws DbException, IOException {
		for (MessageId m : loadUnackedMessageIdsToSend()) {
			if (isInterrupted()) break;
			Message message = db.transactionWithNullableResult(false, txn ->
					db.getMessageToSend(txn, contactId, m, maxLatency, true));
			if (message == null) continue; // No longer shared
			recordWriter.writeMessage(message);
			LOG.info("Sent message");
		}
	}

	private Collection<MessageId> loadUnackedMessageIdsToSend()
			throws DbException {
		Collection<MessageId> ids = db.transactionWithResult(true, txn ->
				db.getUnackedMessagesToSend(txn, contactId));
		if (LOG.isLoggable(INFO)) {
			LOG.info(ids.size() + " unacked messages to send");
		}
		return ids;
	}
}
