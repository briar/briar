package org.briarproject.api.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.MessageContext;

public interface MessageQueueManager {

	/**
	 * The key used for storing the queue's state in the group metadata.
	 */
	String QUEUE_STATE_KEY = "queueState";

	/**
	 * Sends a message using the given queue.
	 */
	QueueMessage sendMessage(Transaction txn, Group queue, long timestamp,
			byte[] body, Metadata meta) throws DbException;

	/**
	 * Sets the message validator for the given client.
	 */
	void registerMessageValidator(ClientId c, QueueMessageValidator v);

	/**
	 * Sets the incoming message hook for the given client. The hook will be
	 * called once for each incoming message that passes validation. Messages
	 * are passed to the hook in order.
	 */
	void registerIncomingMessageHook(ClientId c, IncomingQueueMessageHook hook);

	interface QueueMessageValidator {

		/**
		 * Validates the given message and returns its metadata and
		 * dependencies.
		 */
		MessageContext validateMessage(QueueMessage q, Group g)
				throws InvalidMessageException;
	}

	interface IncomingQueueMessageHook {

		/**
		 * Called once for each incoming message that passes validation.
		 * Messages are passed to the hook in order.
		 *
		 * @throws DbException Should only be used for real database errors.
		 * If this is thrown, delivery will be attempted again at next startup,
		 * whereas if an FormatException is thrown,
		 * the message will be permanently invalidated.
		 * @throws FormatException for any non-database error
		 * that occurs while handling remotely created data.
		 * This includes errors that occur while handling locally created data
		 * in a context controlled by remotely created data
		 * (for example, parsing the metadata of a dependency
		 * of an incoming message).
		 * Never rethrow DbException as FormatException!
		 */
		void incomingMessage(Transaction txn, QueueMessage q, Metadata meta)
				throws DbException, FormatException;
	}
}
