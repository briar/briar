package org.briarproject.android.contact;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.conversation.ConversationManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

// This class is not thread-safe
public class ContactListItem {

	private final Contact contact;
	private final LocalAuthor localAuthor;
	private final GroupId groupId;
	private boolean connected;
	private long timestamp;
	private int unread;

	public ContactListItem(Contact contact, LocalAuthor localAuthor,
			boolean connected,
			GroupId groupId,
			long timestamp, int unread) {
		this.contact = contact;
		this.localAuthor = localAuthor;
		this.groupId = groupId;
		this.connected = connected;
		this.timestamp = timestamp;
		this.unread = unread;
	}

	public Contact getContact() {
		return contact;
	}

	public LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	GroupId getGroupId() {
		return groupId;
	}

	boolean isConnected() {
		return connected;
	}

	void setConnected(boolean connected) {
		this.connected = connected;
	}

	boolean isEmpty() {
		return timestamp < 0;
	}

	long getTimestamp() {
		return timestamp;
	}

	void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	int getUnreadCount() {
		return unread;
	}

	void setUnreadCount(int unread) {
		this.unread = unread;
	}
}