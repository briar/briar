package org.briarproject.api.messaging;

import org.briarproject.api.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageHeader;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface MessagingManager {

	/**
	 * Subscribes to a group, or returns false if the user already has the
	 * maximum number of public subscriptions.
	 */
	boolean addGroup(Group g) throws DbException;

	/** Stores a local message. */
	void addLocalMessage(Message m) throws DbException;

	/** Returns the group with the given ID, if the user subscribes to it. */
	Group getGroup(GroupId g) throws DbException;


	/**
	 * Returns the ID of the inbox group for the given contact, or null if no
	 * inbox group has been set.
	 */
	GroupId getInboxGroupId(ContactId c) throws DbException;

	/**
	 * Returns the headers of all messages in the inbox group for the given
	 * contact, or null if no inbox group has been set.
	 */
	Collection<MessageHeader> getInboxMessageHeaders(ContactId c)
			throws DbException;

	/** Returns the body of the message with the given ID. */
	byte[] getMessageBody(MessageId m) throws DbException;

	/**
	 * Makes a group visible to the given contact, adds it to the contact's
	 * subscriptions, and sets it as the inbox group for the contact.
	 */
	void setInboxGroup(ContactId c, Group g) throws DbException;

	/**
	 * Marks a message as read or unread.
	 */
	void setReadFlag(MessageId m, boolean read) throws DbException;
}
