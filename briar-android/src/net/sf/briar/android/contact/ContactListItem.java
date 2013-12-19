package net.sf.briar.android.contact;

import java.util.Collection;

import net.sf.briar.api.AuthorId;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.MessageHeader;

// This class is not thread-safe
class ContactListItem {

	private final Contact contact;
	private boolean connected;
	private long lastConnected;
	private boolean empty;
	private long timestamp;
	private int unread;

	ContactListItem(Contact contact, boolean connected, long lastConnected,
			Collection<MessageHeader> headers) {
		this.contact = contact;
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

	ContactId getContactId() {
		return contact.getId();
	}

	String getContactName() {
		return contact.getAuthor().getName();
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

	AuthorId getLocalAuthorId() {
		return contact.getLocalAuthorId();
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