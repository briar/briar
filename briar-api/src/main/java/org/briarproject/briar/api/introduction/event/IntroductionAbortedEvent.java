package org.briarproject.briar.api.introduction.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionAbortedEvent extends Event {

	private final ContactId contactId;
	private final SessionId sessionId;

	public IntroductionAbortedEvent(ContactId contactId, SessionId sessionId) {
		this.contactId = contactId;
		this.sessionId = sessionId;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public SessionId getSessionId() {
		return sessionId;
	}
}
