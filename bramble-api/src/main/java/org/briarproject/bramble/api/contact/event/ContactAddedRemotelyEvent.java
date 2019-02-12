package org.briarproject.bramble.api.contact.event;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ContactAddedRemotelyEvent extends Event {

	private final Contact contact;

	public ContactAddedRemotelyEvent(Contact contact) {
		this.contact = contact;
	}

	public Contact getContact() {
		return contact;
	}
}
