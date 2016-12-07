package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactItem {

	private final Contact contact;
	private boolean connected;

	public ContactItem(Contact contact) {
		this(contact, false);
	}

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
