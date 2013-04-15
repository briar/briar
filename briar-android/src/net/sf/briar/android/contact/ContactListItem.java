package net.sf.briar.android.contact;

import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;

// This class is not thread-safe
class ContactListItem {

	private final Contact contact;
	private boolean connected;
	private long lastConnected;

	ContactListItem(Contact contact, boolean connected, long lastConnected) {
		this.contact = contact;
		this.connected = connected;
		this.lastConnected = lastConnected;
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
}