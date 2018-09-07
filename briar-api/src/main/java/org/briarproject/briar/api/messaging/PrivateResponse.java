package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.Nameable;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class PrivateResponse<N extends Nameable>
		extends PrivateMessageHeader {

	private final SessionId sessionId;
	private final N nameable;
	private final boolean accepted;

	public PrivateResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, N nameable, boolean accepted) {
		super(id, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
		this.nameable = nameable;
		this.accepted = accepted;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public N getNameable() {
		return nameable;
	}

	public boolean wasAccepted() {
		return accepted;
	}

}
