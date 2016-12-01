package org.briarproject.bramble.api.contact.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a contact is verified.
 */
@Immutable
@NotNullByDefault
public class ContactVerifiedEvent extends Event {

	private final ContactId contactId;

	public ContactVerifiedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}

}
