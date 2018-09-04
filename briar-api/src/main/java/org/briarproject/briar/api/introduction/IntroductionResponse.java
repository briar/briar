package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.introduction.Role.INTRODUCER;

@Immutable
@NotNullByDefault
public class IntroductionResponse extends PrivateMessageHeader {

	private final SessionId sessionId;
	private final String name;
	private final Role role;
	private final boolean accepted;

	public IntroductionResponse(SessionId sessionId, MessageId messageId,
			GroupId groupId, Role role, long time, boolean local, boolean sent,
			boolean seen, boolean read, String name, boolean accepted) {
		super(messageId, groupId, time, local, sent, seen, read);
		this.sessionId = sessionId;
		this.name = name;
		this.role = role;
		this.accepted = accepted;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public String getName() {
		return name;
	}

	public boolean isIntroducer() {
		return role == INTRODUCER;
	}

	public boolean wasAccepted() {
		return accepted;
	}

}
