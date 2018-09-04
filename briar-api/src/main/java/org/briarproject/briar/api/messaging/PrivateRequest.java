package org.briarproject.briar.api.messaging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PrivateRequest<O extends Nameable> extends PrivateMessageHeader {

	private final SessionId sessionId;
	private final O object;
	@Nullable
	private final String message;
	private final boolean answered, exists;

	public PrivateRequest(MessageId messageId, GroupId groupId, long time,
			boolean local, boolean sent, boolean seen, boolean read,
			SessionId sessionId, O object, @Nullable String message,
			boolean answered, boolean exists) {
		super(messageId, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
		this.object = object;
		this.message = message;
		this.answered = answered;
		this.exists = exists;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public O getObject() {
		return object;
	}

	public String getName() {
		return object.getName();
	}

	@Nullable
	public String getMessage() {
		return message;
	}

	public boolean wasAnswered() {
		return answered;
	}

	public boolean doesExist() {
		return exists;
	}
}
