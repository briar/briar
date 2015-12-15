package org.briarproject.android.contact;

import org.briarproject.api.Contact;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageHeader;

import java.util.Collection;

// This class is not thread-safe
class ContactListItem {

	private final Contact contact;
	private final GroupId inbox;
	private boolean connected, empty;
	private long timestamp;
	private int unread;

	ContactListItem(Contact contact, boolean connected, GroupId inbox,
			Collection<MessageHeader> headers) {
		this.contact = contact;
		this.inbox = inbox;
		this.connected = connected;
		setHeaders(headers);
	}

	void setHeaders(Collection<MessageHeader> headers) {
		empty = headers.isEmpty();
		timestamp = 0;
		unread = 0;
		if (!empty) {
			for (MessageHeader h : headers) {
				if (h.getTimestamp() > timestamp) timestamp = h.getTimestamp();
				if (!h.isRead()) unread++;
			}
		}
	}

	Contact getContact() {
		return contact;
	}

	GroupId getInboxGroupId() {
		return inbox;
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