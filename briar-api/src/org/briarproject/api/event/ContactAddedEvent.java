package org.briarproject.api.event;

import org.briarproject.api.ContactId;

/** An event that is broadcast when a contact is added. */
public class ContactAddedEvent extends Event {

	private final ContactId contactId;

	public ContactAddedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
