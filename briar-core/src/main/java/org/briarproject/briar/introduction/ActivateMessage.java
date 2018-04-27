package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class ActivateMessage extends AbstractIntroductionMessage {

	private final SessionId sessionId;
	private final byte[] mac;

	protected ActivateMessage(MessageId messageId, GroupId groupId,
			long timestamp, MessageId previousMessageId, SessionId sessionId,
			byte[] mac) {
		super(messageId, groupId, timestamp, previousMessageId);
		this.sessionId = sessionId;
		this.mac = mac;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public byte[] getMac() {
		return mac;
	}

}
