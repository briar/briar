package org.briarproject.android.contact;

import android.support.annotation.Nullable;

import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;

import static org.briarproject.android.contact.ConversationItem.IncomingItem;

// This class is NOT thread-safe
public class ContactListItem {

	private final Contact contact;
	private final LocalAuthor localAuthor;
	private final GroupId groupId;
	private boolean connected, empty;
	private long timestamp;
	private long unread;

	public ContactListItem(Contact contact, LocalAuthor localAuthor,
			boolean connected, @Nullable GroupId groupId, GroupCount count) {
		this.contact = contact;
		this.localAuthor = localAuthor;
		this.groupId = groupId;
		this.connected = connected;
		this.empty = count.getMsgCount() == 0;
		this.unread = count.getUnreadCount();
		this.timestamp = count.getLatestMsgTime();
	}

	void addMessage(ConversationItem message) {
		empty = empty && message == null;
		if (message != null) {
			if (message.getTime() > timestamp) timestamp = message.getTime();
			if (message instanceof IncomingItem &&
					!((IncomingItem) message).isRead())
				unread++;
		}
	}

	public Contact getContact() {
		return contact;
	}

	public LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	@Nullable
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
		return empty;
	}

	long getTimestamp() {
		return timestamp;
	}

	long getUnreadCount() {
		return unread;
	}
}