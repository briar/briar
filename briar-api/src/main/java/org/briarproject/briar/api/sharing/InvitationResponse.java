package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class InvitationResponse extends InvitationMessage {

	private final GroupId shareableId;
	private final boolean accept;

	public InvitationResponse(MessageId id, GroupId groupId,
			long time, boolean local, boolean sent, boolean seen,
			boolean read, SessionId sessionId, GroupId shareableId,
			ContactId contactId, boolean accept) {
		super(id, groupId, time, local, sent, seen, read, sessionId, contactId);
		this.shareableId = shareableId;
		this.accept = accept;
	}

	public boolean wasAccepted() {
		return accept;
	}

	public GroupId getShareableId() {
		return shareableId;
	}

}
