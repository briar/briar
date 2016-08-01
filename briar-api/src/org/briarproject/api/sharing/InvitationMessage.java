package org.briarproject.api.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.messaging.BaseMessage;
import org.briarproject.api.sync.MessageId;

public abstract class InvitationMessage extends BaseMessage {

	private final SessionId sessionId;
	private final ContactId contactId;

	public InvitationMessage(MessageId id, SessionId sessionId,
			ContactId contactId, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, time, local, read, sent, seen);
		this.sessionId = sessionId;
		this.contactId = contactId;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public ContactId getContactId() {
		return contactId;
	}

}
