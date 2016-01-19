package org.briarproject.api.messaging;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface MessagingManager {

	/** Returns the unique ID of the messaging client. */
	ClientId getClientId();

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
	byte[] getMessageBody(MessageId m) throws DbException;

	/** Marks a private message as read or unread. */
	void setReadFlag(MessageId m, boolean read) throws DbException;
}
