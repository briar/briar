package org.briarproject.api.introduction;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.MessageId;

public class IntroductionRequest extends IntroductionResponse {

	private final String message;
	private final boolean answered, exists, introducesOtherIdentity;

	public IntroductionRequest(SessionId sessionId, MessageId messageId,
			int role, long time, boolean local, boolean sent, boolean seen,
			boolean read, AuthorId authorId, String name, boolean accepted,
			String message, boolean answered, boolean exists,
			boolean introducesOtherIdentity) {

		super(sessionId, messageId, role, time, local, sent, seen, read,
				authorId, name, accepted);

		this.message = message;
		this.answered = answered;
		this.exists = exists;
		this.introducesOtherIdentity = introducesOtherIdentity;
	}

	public String getMessage() {
		return message;
	}

	public boolean wasAnswered() {
		return answered;
	}

	public boolean contactExists() {
		return exists;
	}

	public boolean doesIntroduceOtherIdentity() {
		return introducesOtherIdentity;
	}
}
