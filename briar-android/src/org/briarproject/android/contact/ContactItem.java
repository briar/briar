package org.briarproject.android.contact;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactItem {

	private final Contact contact;

	public ContactItem(Contact contact) {
		this.contact = contact;
	}

	public Contact getContact() {
		return contact;
	}

}
