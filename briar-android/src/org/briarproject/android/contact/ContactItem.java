package org.briarproject.android.contact;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactItem {

	private final Contact contact;

	private boolean connected;

	public ContactItem(Contact contact, boolean connected) {
		this.contact = contact;
		this.connected = connected;
	}

	public Contact getContact() {
		return contact;
	}

	boolean isConnected() {
		return connected;
	}

	void setConnected(boolean connected) {
		this.connected = connected;
	}

}
