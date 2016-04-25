package org.briarproject.api.introduction;

import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.MessageId;

public class IntroductionResponse extends IntroductionMessage {

	private final AuthorId remoteAuthorId;
	private final String name;
	private final boolean accepted;

	public IntroductionResponse(SessionId sessionId, MessageId messageId,
			int role, long time, boolean local, boolean sent, boolean seen,
			boolean read, AuthorId remoteAuthorId, String name,
			boolean accepted) {

		super(sessionId, messageId, role, time, local, sent, seen, read);

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
