package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationRequest;

public abstract class InvitationReceivedEvent extends Event {

	private final ContactId contactId;
	private final InvitationRequest request;

	InvitationReceivedEvent(ContactId contactId, InvitationRequest request) {
		this.contactId = contactId;
		this.request = request;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public InvitationRequest getRequest() {
		return request;
	}
}
