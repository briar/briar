package org.briarproject.android.contact;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

// This class is not thread-safe
class ContactListItem {

	private final Contact contact;
	private final GroupId conversation;
	private boolean connected, empty;
	private long timestamp;
	private int unread;

	ContactListItem(Contact contact, boolean connected, GroupId conversation,
			Collection<PrivateMessageHeader> headers) {
		this.contact = contact;
		this.conversation = conversation;
		this.connected = connected;
		setHeaders(headers);
	}

	void setHeaders(Collection<PrivateMessageHeader> headers) {
		empty = headers.isEmpty();
		timestamp = 0;
		unread = 0;
		if (!empty) {
			for (PrivateMessageHeader h : headers) {
				if (h.getTimestamp() > timestamp) timestamp = h.getTimestamp();
				if (!h.isRead()) unread++;
			}
		}
	}

	Contact getContact() {
		return contact;
	}

	GroupId getConversationId() {
		return conversation;
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

	int getUnreadCount() {
		return unread;
	}
}