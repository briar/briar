package org.briarproject.android.contact;

import org.briarproject.api.Contact;

public class ContactItem {

	public static final ContactItem NEW = new ContactItem(null);

	private final Contact contact;

	public ContactItem(Contact contact) {
		this.contact = contact;
	}

	public Contact getContact() {
		return contact;
	}
}
