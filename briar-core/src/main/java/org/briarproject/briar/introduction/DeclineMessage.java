package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class DeclineMessage extends AbstractIntroductionMessage {

	private final SessionId sessionId;

	protected DeclineMessage(MessageId messageId, GroupId groupId,
			long timestamp, @Nullable MessageId previousMessageId,
			SessionId sessionId, long autoDeleteTimer) {
		super(messageId, groupId, timestamp, previousMessageId,
				autoDeleteTimer);
		this.sessionId = sessionId;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

}
