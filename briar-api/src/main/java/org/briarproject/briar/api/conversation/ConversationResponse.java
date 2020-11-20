package org.briarproject.briar.api.conversation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class ConversationResponse extends ConversationMessageHeader {

	private final SessionId sessionId;
	private final boolean accepted;

	public ConversationResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean read, boolean sent, boolean seen,
			SessionId sessionId, boolean accepted, long autoDeleteTimer) {
		super(id, groupId, time, local, read, sent, seen, autoDeleteTimer);
		this.sessionId = sessionId;
		this.accepted = accepted;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public boolean wasAccepted() {
		return accepted;
	}

}
