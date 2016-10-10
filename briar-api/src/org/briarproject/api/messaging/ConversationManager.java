package org.briarproject.api.messaging;

import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.GroupId;

public interface ConversationManager {

	/**
	 * Clients that present messages in a private conversation need to
	 * register themselves here.
	 */
	void registerConversationClient(ConversationClient client);

	/** Get the main group ID that represents this conversation */
	GroupId getConversationId(ContactId contactId) throws DbException;

	/** Get the unified group count for all private conversation messages. */
	GroupCount getGroupCount(ContactId contactId) throws DbException;

	interface ConversationClient {
		GroupCount getGroupCount(Transaction txn, ContactId contactId)
				throws DbException;
	}

}
