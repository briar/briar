package org.briarproject.briar.api.sharing.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.sharing.InvitationResponse;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
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
