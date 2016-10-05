package org.briarproject.api.introduction;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

public class IntroductionResponse extends IntroductionMessage {

	private final AuthorId remoteAuthorId;
	private final String name;
	private final boolean accepted;

	public IntroductionResponse(SessionId sessionId, MessageId messageId,
			GroupId groupId, int role, long time, boolean local, boolean sent,
			boolean seen, boolean read, AuthorId remoteAuthorId, String name,
			boolean accepted) {

		super(sessionId, messageId, groupId, role, time, local, sent, seen,
				read);

		this.remoteAuthorId = remoteAuthorId;
		this.name = name;
		this.accepted = accepted;
	}

	public String getName() {
		return name;
	}

	public boolean wasAccepted() {
		return accepted;
	}

	public AuthorId getRemoteAuthorId() {
		return remoteAuthorId;
	}
}
