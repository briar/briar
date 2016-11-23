package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

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
