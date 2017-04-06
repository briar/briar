package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.BaseMessageHeader;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE_INTRODUCER;

@Immutable
@NotNullByDefault
public class IntroductionMessage extends BaseMessageHeader {

	private final SessionId sessionId;
	private final MessageId messageId;
	private final int role;

	IntroductionMessage(SessionId sessionId, MessageId messageId,
			GroupId groupId, int role, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(messageId, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
		this.messageId = messageId;
		this.role = role;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public boolean isIntroducer() {
		return role == ROLE_INTRODUCER;
	}

}
