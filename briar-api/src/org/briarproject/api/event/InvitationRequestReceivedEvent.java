package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sharing.Shareable;

public abstract class InvitationRequestReceivedEvent<S extends Shareable>
		extends Event {

	private final S shareable;
	private final ContactId contactId;
	private final InvitationRequest request;

	InvitationRequestReceivedEvent(S shareable, ContactId contactId,
			InvitationRequest request) {
		this.shareable = shareable;
		this.contactId = contactId;
		this.request = request;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public InvitationRequest getRequest() {
		return request;
	}

	public S getShareable() {
		return shareable;
	}
}
