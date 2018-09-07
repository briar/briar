package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PrivateRequest<N extends Nameable> extends PrivateMessageHeader {

	private final SessionId sessionId;
	private final N nameable;
	@Nullable
	private final String message;
	private final boolean answered;

	public PrivateRequest(MessageId messageId, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, N nameable, @Nullable String message,
			boolean answered) {
		super(messageId, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
		this.nameable = nameable;
		this.message = message;
		this.answered = answered;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public N getNameable() {
		return nameable;
	}

	public String getName() {
		return nameable.getName();
	}

	@Nullable
	public String getMessage() {
		return message;
	}

	public boolean wasAnswered() {
		return answered;
	}
}
