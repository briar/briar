package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class InvitationResponse extends PrivateMessageHeader {

	private final SessionId sessionId;
	private final GroupId shareableId;
	private final boolean accept;

	public InvitationResponse(MessageId id, GroupId groupId,
			long time, boolean local, boolean sent, boolean seen,
			boolean read, SessionId sessionId, GroupId shareableId,
			boolean accept) {
		super(id, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
		this.shareableId = shareableId;
		this.accept = accept;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public boolean wasAccepted() {
		return accept;
	}

	public GroupId getShareableId() {
		return shareableId;
	}

}
