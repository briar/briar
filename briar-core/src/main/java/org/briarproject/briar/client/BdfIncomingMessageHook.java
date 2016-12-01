package org.briarproject.briar.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.briar.api.client.MessageQueueManager.IncomingQueueMessageHook;
import org.briarproject.briar.api.client.QueueMessage;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.briar.api.client.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;

@Immutable
@NotNullByDefault
public abstract class BdfIncomingMessageHook implements IncomingMessageHook,
		IncomingQueueMessageHook {

	protected final DatabaseComponent db;
	protected final ClientHelper clientHelper;
	protected final MetadataParser metadataParser;

	protected BdfIncomingMessageHook(DatabaseComponent db,
			ClientHelper clientHelper, MetadataParser metadataParser) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.metadataParser = metadataParser;
	}

	/**
	 * Called once for each incoming message that passes validation.
	 *
	 * @throws DbException Should only be used for real database errors.
	 * If this is thrown, delivery will be attempted again at next startup,
	 * whereas if a FormatException is thrown, the message will be permanently
	 * invalidated.
	 * @throws FormatException Use this for any non-database error
	 * that occurs while handling remotely created data.
	 * This includes errors that occur while handling locally created data
	 * in a context controlled by remotely created data
	 * (for example, parsing the metadata of a dependency
	 * of an incoming message).
	 * Never rethrow DbException as FormatException!
	 */
	protected abstract boolean incomingMessage(Transaction txn, Message m,
			BdfList body, BdfDictionary meta) throws DbException,
			FormatException;

	@Override
	public boolean incomingMessage(Transaction txn, Message m, Metadata meta)
			throws DbException, InvalidMessageException {
		try {
			return incomingMessage(txn, m, meta, MESSAGE_HEADER_LENGTH);
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
	}

	@Override
	public void incomingMessage(Transaction txn, QueueMessage q, Metadata meta)
			throws DbException, InvalidMessageException {
		try {
			incomingMessage(txn, q, meta, QUEUE_MESSAGE_HEADER_LENGTH);
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
	}

	private boolean incomingMessage(Transaction txn, Message m, Metadata meta,
			int headerLength) throws DbException, FormatException {
		byte[] raw = m.getRaw();
		BdfList body = clientHelper.toList(raw, headerLength,
				raw.length - headerLength);
		BdfDictionary metaDictionary = metadataParser.parse(meta);
		return incomingMessage(txn, m, body, metaDictionary);
	}

}
