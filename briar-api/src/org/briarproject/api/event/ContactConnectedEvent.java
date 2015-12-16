package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

/**
 * An event that is broadcast when a contact connects that was not previously
 * connected via any transport.
 */
public class ContactConnectedEvent extends Event {

	private final ContactId contactId;

	public ContactConnectedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
