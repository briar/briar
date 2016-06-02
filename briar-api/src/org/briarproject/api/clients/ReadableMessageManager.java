package org.briarproject.api.clients;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.MessageId;

public interface ReadableMessageManager {

	/**
	 * Returns the timestamp of the latest message in the group with the given
	 * contact, or -1 if the group is empty.
	 */
	long getTimestamp(ContactId c) throws DbException;

	/**
	 * Returns the number of unread messages in the group with the given
	 * contact.
	 */
	int getUnreadCount(ContactId c) throws DbException;

	/**
	 * Marks a message as read or unread.
	 */
	void setReadFlag(ContactId c, MessageId m, boolean local, boolean read)
			throws DbException;
}
