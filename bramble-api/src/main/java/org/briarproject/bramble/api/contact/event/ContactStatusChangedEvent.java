package org.briarproject.bramble.api.contact.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a contact is marked active or inactive.
 */
@Immutable
@NotNullByDefault
public class ContactStatusChangedEvent extends Event {

	private final ContactId contactId;
	private final boolean active;

	public ContactStatusChangedEvent(ContactId contactId, boolean active) {
		this.contactId = contactId;
		this.active = active;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public boolean isActive() {
		return active;
	}
}
