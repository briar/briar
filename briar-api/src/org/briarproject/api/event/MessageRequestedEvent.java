package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

/** An event that is broadcast when a message is requested by a contact. */
public class MessageRequestedEvent extends Event {

	private final ContactId contactId;

	public MessageRequestedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
