package org.briarproject.briar.api.sharing.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.messaging.PrivateResponse;
import org.briarproject.briar.api.sharing.Shareable;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class InvitationResponseReceivedEvent<S extends Shareable>
		extends Event {

	private final ContactId contactId;
	private final PrivateResponse<S> response;

	public InvitationResponseReceivedEvent(ContactId contactId,
			PrivateResponse<S> response) {
		this.contactId = contactId;
		this.response = response;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public PrivateResponse<S> getResponse() {
		return response;
	}
}
