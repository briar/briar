package org.briarproject.api.introduction;

import org.briarproject.api.clients.BaseMessageHeader;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;

import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;

public class IntroductionMessage extends BaseMessageHeader {

	private final SessionId sessionId;
	private final MessageId messageId;
	private final int role;

	public IntroductionMessage(@NotNull SessionId sessionId,
			@NotNull MessageId messageId, @NotNull GroupId groupId, int role,
			long time, boolean local, boolean sent, boolean seen,
			boolean read) {

		super(messageId, groupId, time, local, read, sent, seen);
		this.sessionId = sessionId;
		this.messageId = messageId;
		this.role = role;
	}

	@NotNull
	public SessionId getSessionId() {
		return sessionId;
	}

	@NotNull
	public MessageId getMessageId() {
		return messageId;
	}

	public boolean isIntroducer() {
		return role == ROLE_INTRODUCER;
	}

}
