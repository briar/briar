package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

/** An event that is broadcast when a contact is added. */
public class ContactAddedEvent extends Event {

	private final ContactId contactId;
	private final boolean active;

	public ContactAddedEvent(ContactId contactId, boolean active) {
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
