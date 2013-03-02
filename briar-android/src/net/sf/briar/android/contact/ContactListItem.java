package net.sf.briar.android.contact;

import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;

// This class is not thread-safe
class ContactListItem {

	final Contact contact;
	private boolean connected;

	ContactListItem(Contact contact, boolean connected) {
		this.contact = contact;
		this.connected = connected;
	}

	ContactId getContactId() {
		return contact.getId();
	}

	String getName() {
		return contact.getName();
	}

	long getLastConnected() {
		return contact.getLastConnected();
	}

	boolean isConnected() {
		return connected;
	}

	void setConnected(boolean connected) {
		this.connected = connected;
	}
}