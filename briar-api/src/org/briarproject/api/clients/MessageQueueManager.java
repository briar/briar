package org.briarproject.api.clients;

import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;

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

	interface IncomingQueueMessageHook {
		void incomingMessage(Transaction txn, QueueMessage q, Metadata meta)
				throws DbException;
	}
}
