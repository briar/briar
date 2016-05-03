package org.briarproject.api.introduction;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;

abstract public class IntroductionMessage {

	private final SessionId sessionId;
	private final MessageId messageId;
	private final int role;
	private final long time;
	private final boolean local, sent, seen, read;

	public IntroductionMessage(SessionId sessionId, MessageId messageId,
			int role, long time, boolean local, boolean sent, boolean seen,
			boolean read) {

		this.sessionId = sessionId;
		this.messageId = messageId;
		this.role = role;
		this.time = time;
		this.local = local;
		this.sent = sent;
		this.seen = seen;
		this.read = read;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public long getTime() {
		return time;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public boolean isLocal() {
		return local;
	}

	public boolean isSent() {
		return sent;
	}

	public boolean isSeen() {
		return seen;
	}

	public boolean isRead() {
		return read;
	}

	public boolean isIntroducer() {
		return role == ROLE_INTRODUCER;
	}

	public boolean isIntroducee() {
		return role == ROLE_INTRODUCEE;
	}

}

