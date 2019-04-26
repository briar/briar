package org.briarproject.bramble.api.sync.validation;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;

public interface IncomingMessageHook {

	/**
	 * Called once for each incoming message that passes validation.
	 *
	 * @param txn A read-write transaction
	 * @return Whether or not this message should be shared
	 * @throws DbException Should only be used for real database errors.
	 * If this is thrown, delivery will be attempted again at next startup,
	 * whereas if an InvalidMessageException is thrown,
	 * the message will be permanently invalidated.
	 * @throws InvalidMessageException for any non-database error
	 * that occurs while handling remotely created data.
	 * This includes errors that occur while handling locally created data
	 * in a context controlled by remotely created data
	 * (for example, parsing the metadata of a dependency
	 * of an incoming message).
	 * Throwing this will delete the incoming message and its metadata
	 * marking it as invalid in the database.
	 * Never rethrow DbException as InvalidMessageException!
	 */
	boolean incomingMessage(Transaction txn, Message m, Metadata meta)
			throws DbException, InvalidMessageException;
}
