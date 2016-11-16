package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager.IncomingQueueMessageHook;
import org.briarproject.api.clients.QueueMessage;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.ValidationManager.IncomingMessageHook;

import static org.briarproject.api.clients.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

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
