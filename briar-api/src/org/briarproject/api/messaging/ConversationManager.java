package org.briarproject.api.messaging;

import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

@NotNullByDefault
public interface ConversationManager {

	/**
	 * Clients that present messages in a private conversation need to
	 * register themselves here.
	 */
	void registerConversationClient(ConversationClient client);

	/**
	 * Get the unified group count for all private conversation messages.
	 */
	GroupCount getGroupCount(ContactId c) throws DbException;

	interface ConversationClient {

		Group getContactGroup(Contact c);

		GroupCount getGroupCount(Transaction txn, ContactId c)
				throws DbException;

		void setReadFlag(GroupId g, MessageId m, boolean read)
				throws DbException;
	}

}
