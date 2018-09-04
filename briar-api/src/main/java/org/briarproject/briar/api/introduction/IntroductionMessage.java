package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class IntroductionMessage extends PrivateMessageHeader {

	private final SessionId sessionId;

	IntroductionMessage(SessionId sessionId, MessageId messageId,
			GroupId groupId, long time, boolean local, boolean sent,
			boolean seen, boolean read) {
		super(messageId, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

}
