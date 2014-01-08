package org.briarproject.android.contact;

import java.util.Collection;

import org.briarproject.api.Contact;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.messaging.GroupId;

// This class is not thread-safe
class ContactListItem {

	private final Contact contact;
	private final GroupId inbox;
	private boolean connected;
	private long lastConnected;
	private boolean empty;
	private long timestamp;
	private int unread;

	ContactListItem(Contact contact, boolean connected, long lastConnected,
			GroupId inbox, Collection<MessageHeader> headers) {
		this.contact = contact;
		this.inbox = inbox;
		this.connected = connected;
		this.lastConnected = lastConnected;
		setHeaders(headers);
	}

	void setHeaders(Collection<MessageHeader> headers) {
		empty = headers.isEmpty();
		timestamp = 0;
		unread = 0;
		if(!empty) {
			for(MessageHeader h : headers) {
				if(h.getTimestamp() > timestamp) timestamp = h.getTimestamp();
				if(!h.isRead()) unread++;
			}
		}
	}

	Contact getContact() {
		return contact;
	}

	GroupId getInboxGroupId() {
		return inbox;
	}

	long getLastConnected() {
		return lastConnected;
	}

	void setLastConnected(long lastConnected) {
		this.lastConnected = lastConnected;
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