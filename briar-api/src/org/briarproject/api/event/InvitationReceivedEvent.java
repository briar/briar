package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

public abstract class InvitationReceivedEvent extends Event {

	private final ContactId contactId;

	InvitationReceivedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
