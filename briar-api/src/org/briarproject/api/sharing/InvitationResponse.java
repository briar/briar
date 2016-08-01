package org.briarproject.api.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.MessageId;

public abstract class InvitationResponse extends InvitationMessage {

	private final boolean accept;

	public InvitationResponse(MessageId id, SessionId sessionId,
			ContactId contactId, boolean accept, long time, boolean local,
			boolean sent, boolean seen, boolean read) {

		super(id, sessionId, contactId, time, local, read, sent, seen);
		this.accept = accept;
	}

	public boolean wasAccepted() {
		return accept;
	}
}
