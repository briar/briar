package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

/**
 * An event that is broadcast when a contact disconnects and is no longer
 * connected via any transport.
 */
public class ContactDisconnectedEvent extends Event {

	private final ContactId contactId;

	public ContactDisconnectedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
