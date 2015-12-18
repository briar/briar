package org.briarproject.api.messaging;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface MessagingManager {

	/**
	 * Informs the messaging manager that a new contact has been added.
	 * Creates a private conversation with the contact.
	 */
	void addContact(ContactId c, SecretKey master) throws DbException;

	/** Stores a local private message. */
	void addLocalMessage(Message m) throws DbException;

	/** Returns the private conversation with the given ID. */
	PrivateConversation getConversation(GroupId g) throws DbException;

	/**
	 * Returns the ID of the private conversation with the given contact, or
	 * null if no private conversation ID has been set.
	 */
	GroupId getConversationId(ContactId c) throws DbException;

	/**
	 * Returns the headers of all messages in the private conversation with the
	 * given contact, or null if no private conversation ID has been set.
	 */
	Collection<PrivateMessageHeader> getMessageHeaders(ContactId c)
			throws DbException;

	/** Returns the body of the private message with the given ID. */
	byte[] getMessageBody(MessageId m) throws DbException;

	/**
	 * Makes a private conversation visible to the given contact, adds it to
	 * the contact's subscriptions, and sets it as the private conversation for
	 * the contact.
	 */
	void setConversation(ContactId c, PrivateConversation p) throws DbException;

	/** Marks a private message as read or unread. */
	void setReadFlag(MessageId m, boolean read) throws DbException;
}
