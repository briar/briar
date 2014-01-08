package org.briarproject.api.event;

import org.briarproject.api.ContactId;

/**
 * An event that is broadcast when a message is offered by a contact and needs
 * to be requested.
 */
public class MessageToRequestEvent extends Event {

	private final ContactId contactId;

	public MessageToRequestEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
