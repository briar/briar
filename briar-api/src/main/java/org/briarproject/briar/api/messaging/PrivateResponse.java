package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class PrivateResponse<O extends Nameable>
		extends PrivateMessageHeader {

	private final SessionId sessionId;
	private final O object;
	private final boolean accepted;

	public PrivateResponse(MessageId id, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, O object, boolean accepted) {
		super(id, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
		this.object = object;
		this.accepted = accepted;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public O getObject() {
		return object;
	}

	public boolean wasAccepted() {
		return accepted;
	}

}
