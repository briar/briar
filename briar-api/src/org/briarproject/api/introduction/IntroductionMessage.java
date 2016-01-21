package org.briarproject.api.introduction;

import org.briarproject.api.sync.MessageId;

abstract public class IntroductionMessage {

	private final SessionId sessionId;
	private final MessageId messageId;
	private final long time;
	private final boolean local, sent, seen, read;

	public IntroductionMessage(SessionId sessionId, MessageId messageId,
			long time, boolean local, boolean sent, boolean seen,
			boolean read) {

		this.sessionId = sessionId;
		this.messageId = messageId;
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

}

