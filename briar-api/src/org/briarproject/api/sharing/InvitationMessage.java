package org.briarproject.api.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.messaging.BaseMessage;
import org.briarproject.api.sync.MessageId;

public abstract class InvitationMessage extends BaseMessage {

	private final SessionId sessionId;
	private final ContactId contactId;
	private final String message;
	private final boolean available;

	public InvitationMessage(MessageId id, SessionId sessionId,
			ContactId contactId, String message,
			boolean available, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, time, local, read, sent, seen);
		this.sessionId = sessionId;
		this.contactId = contactId;
		this.message = message;
		this.available = available;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public String getMessage() {
		return message;
	}

	public boolean isAvailable() {
		return available;
	}

}
