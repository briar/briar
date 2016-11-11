package org.briarproject.api.messaging;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.ConversationManager.ConversationClient;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

@NotNullByDefault
public interface MessagingManager extends ConversationClient {

	/** The unique ID of the messaging client. */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.messaging");

	/** Stores a local private message. */
	void addLocalMessage(PrivateMessage m) throws DbException;

	/** Returns the ID of the contact with the given private conversation. */
	ContactId getContactId(GroupId g) throws DbException;

	/** Returns the ID of the private conversation with the given contact. */
	GroupId getConversationId(ContactId c) throws DbException;

	/**
	 * Returns the headers of all messages in the given private conversation.
	 */
	Collection<PrivateMessageHeader> getMessageHeaders(ContactId c)
			throws DbException;

	/** Returns the body of the private message with the given ID. */
	String getMessageBody(MessageId m) throws DbException;

}
