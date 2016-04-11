package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.introduction.SessionId;

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
