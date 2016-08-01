package org.briarproject.api.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.MessageId;

public abstract class InvitationRequest extends InvitationMessage {

	private final String message;
	private final boolean available;

	public InvitationRequest(MessageId id, SessionId sessionId,
			ContactId contactId, String message,
			boolean available, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, sessionId, contactId, time, local, read, sent, seen);
		this.message = message;
		this.available = available;
	}

	public String getMessage() {
		return message;
	}

	public boolean isAvailable() {
		return available;
	}

}
