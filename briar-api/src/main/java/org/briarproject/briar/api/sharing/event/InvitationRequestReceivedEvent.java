package org.briarproject.briar.api.sharing.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.sharing.Shareable;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class InvitationRequestReceivedEvent<S extends Shareable>
		extends Event {

	private final S shareable;
	private final ContactId contactId;
	private final PrivateRequest<S> request;

	protected InvitationRequestReceivedEvent(S shareable, ContactId contactId,
			PrivateRequest<S> request) {
		this.shareable = shareable;
		this.contactId = contactId;
		this.request = request;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public PrivateRequest<S> getRequest() {
		return request;
	}

	public S getShareable() {
		return shareable;
	}
}
