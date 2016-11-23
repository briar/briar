package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;

@NotNullByDefault
public interface ConversationManager {

	/**
	 * Clients that present messages in a private conversation need to
	 * register themselves here.
	 */
	void registerConversationClient(ConversationClient client);

	/**
	 * Returns the unified group count for all private conversation messages.
	 */
	GroupCount getGroupCount(ContactId c) throws DbException;

	@NotNullByDefault
	interface ConversationClient {

		Group getContactGroup(Contact c);

		GroupCount getGroupCount(Transaction txn, ContactId c)
				throws DbException;

		void setReadFlag(GroupId g, MessageId m, boolean read)
				throws DbException;
	}

}
