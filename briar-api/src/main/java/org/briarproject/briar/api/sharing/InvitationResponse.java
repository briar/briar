package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class InvitationResponse extends InvitationMessage {

	private final boolean accept;

	public InvitationResponse(MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, boolean accept, long time,
			boolean local, boolean sent, boolean seen, boolean read) {

		super(id, sessionId, groupId, contactId, time, local, sent, seen, read);
		this.accept = accept;
	}

	public boolean wasAccepted() {
		return accept;
	}
}
