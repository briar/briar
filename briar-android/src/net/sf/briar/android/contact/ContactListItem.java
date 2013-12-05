package net.sf.briar.android.contact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.sf.briar.android.DescendingHeaderComparator;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.PrivateMessageHeader;

// This class is not thread-safe
class ContactListItem {

	private final Contact contact;
	private boolean connected;
	private long lastConnected;
	private final boolean empty;
	private final long timestamp;
	private final int unread;

	ContactListItem(Contact contact, boolean connected, long lastConnected,
			Collection<PrivateMessageHeader> headers) {
		this.contact = contact;
		this.connected = connected;
		this.lastConnected = lastConnected;
		empty = headers.isEmpty();
		if(empty) {
			timestamp = 0;
			unread = 0;
		} else {
			List<PrivateMessageHeader> list =
					new ArrayList<PrivateMessageHeader>(headers);
			Collections.sort(list, DescendingHeaderComparator.INSTANCE);
			timestamp = list.get(0).getTimestamp();
			int unread = 0;
			for(PrivateMessageHeader h : list) if(!h.isRead()) unread++;
			this.unread = unread;
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