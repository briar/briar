package org.briarproject.api.introduction;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.messaging.BaseMessage;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;

 public class IntroductionMessage extends BaseMessage {

	private final SessionId sessionId;
	private final MessageId messageId;
	private final int role;

	public IntroductionMessage(SessionId sessionId, MessageId messageId,
			int role, long time, boolean local, boolean sent, boolean seen,
			boolean read) {

		super(messageId, time, local, read, sent, seen);
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

	public boolean isIntroducee() {
		return role == ROLE_INTRODUCEE;
	}

}

