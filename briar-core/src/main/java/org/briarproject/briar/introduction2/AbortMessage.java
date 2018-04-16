package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class AbortMessage extends IntroductionMessage {

	private final SessionId sessionId;

	protected AbortMessage(MessageId messageId, GroupId groupId, long timestamp,
			@Nullable MessageId previousMessageId, SessionId sessionId) {
		super(messageId, groupId, timestamp, previousMessageId);
		this.sessionId = sessionId;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

}
