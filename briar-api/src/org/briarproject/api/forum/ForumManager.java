package org.briarproject.api.forum;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageHeader;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface ForumManager {

	/**
	 * Subscribes to a group, or returns false if the user already has the
	 * maximum number of public subscriptions.
	 */
	boolean addGroup(Group g) throws DbException;

	/** Stores a local message. */
	void addLocalMessage(Message m) throws DbException;

	/** Returns all groups to which the user could subscribe. */
	Collection<Group> getAvailableGroups() throws DbException;

	/** Returns the group with the given ID, if the user subscribes to it. */
	Group getGroup(GroupId g) throws DbException;

	/** Returns all groups to which the user subscribes, excluding inboxes. */
	Collection<Group> getGroups() throws DbException;

	/** Returns the body of the message with the given ID. */
	byte[] getMessageBody(MessageId m) throws DbException;

	/** Returns the headers of all messages in the given group. */
	Collection<MessageHeader> getMessageHeaders(GroupId g)
			throws DbException;

	/** Returns all contacts who subscribe to the given group. */
	Collection<Contact> getSubscribers(GroupId g) throws DbException;

	/** Returns the IDs of all contacts to which the given group is visible. */
	Collection<ContactId> getVisibility(GroupId g) throws DbException;

	/**
	 * Unsubscribes from a group. Any messages belonging to the group
	 * are deleted.
	 */
	void removeGroup(Group g) throws DbException;

	/**
	 * Marks a message as read or unread.
	 */
	void setReadFlag(MessageId m, boolean read) throws DbException;

	/**
	 * Makes a group visible to the given set of contacts and invisible to any
	 * other current or future contacts.
	 */
	void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException;

	/**
	 * Makes a group visible to all current and future contacts, or invisible
	 * to future contacts.
	 */
	void setVisibleToAll(GroupId g, boolean all) throws DbException;
}
