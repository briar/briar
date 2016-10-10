package org.briarproject.api.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;

public abstract class InvitationResponse extends InvitationMessage {

	private final boolean accept;

	public InvitationResponse(@NotNull MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, boolean accept, long time,
			boolean local, boolean sent, boolean seen, boolean read) {

		super(id, sessionId, groupId, contactId, time, local, sent, seen, read);
		this.accept = accept;
	}

	public boolean wasAccepted() {
		return accept;
	}
}
