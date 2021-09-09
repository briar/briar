package org.briarproject.bramble.api.client;

import org.briarproject.bramble.api.FormatException;
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
import org.briarproject.bramble.api.sync.validation.IncomingMessageHook;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class BdfIncomingMessageHook implements IncomingMessageHook {

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
	 * <p>
	 * If an unexpected exception occurs while handling data that is assumed
	 * to be valid (e.g. locally created metadata), it may be sensible to
	 * rethrow the unexpected exception as a DbException so that delivery is
	 * attempted again at next startup. This will allow delivery to succeed if
	 * the unexpected exception was caused by a bug that has subsequently been
	 * fixed.
	 *
	 * @param txn A read-write transaction
	 * @throws DbException if a database error occurs while delivering the
	 * message. Delivery will be attempted again at next startup. Throwing
	 * this exception has the same effect as returning
	 * {@link DeliveryAction#DEFER}.
	 * @throws FormatException if the message is invalid in the context of its
	 * dependencies. The message and any dependents will be marked as invalid
	 * and deleted along with their metadata. Throwing this exception has the
	 * same effect as returning {@link DeliveryAction#REJECT}.
	 */
	protected abstract DeliveryAction incomingMessage(Transaction txn,
			Message m, BdfList body, BdfDictionary meta)
			throws DbException, FormatException;

	@Override
	public DeliveryAction incomingMessage(Transaction txn, Message m,
			Metadata meta) throws DbException, InvalidMessageException {
		try {
			BdfList body = clientHelper.toList(m);
			BdfDictionary metaDictionary = metadataParser.parse(meta);
			return incomingMessage(txn, m, body, metaDictionary);
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
	}
}
