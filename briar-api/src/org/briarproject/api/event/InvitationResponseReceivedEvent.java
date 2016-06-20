package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

public abstract class InvitationResponseReceivedEvent extends Event {

	private final ContactId contactId;

	public InvitationResponseReceivedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
