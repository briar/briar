package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationResponse;

public abstract class InvitationResponseReceivedEvent extends Event {

	private final ContactId contactId;
	private final InvitationResponse response;

	public InvitationResponseReceivedEvent(ContactId contactId,
			InvitationResponse response) {
		this.contactId = contactId;
		this.response = response;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public InvitationResponse getResponse() {
		return response;
	}
}
