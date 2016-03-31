package org.briarproject.api.introduction;

import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.MessageId;

public class IntroductionRequest extends IntroductionResponse {

	private final String message;
	private final boolean answered, exists;

	public IntroductionRequest(SessionId sessionId, MessageId messageId,
			long time, boolean local, boolean sent, boolean seen, boolean read,
			AuthorId authorId, String name, boolean accepted, String message,
			boolean answered, boolean exists) {

		super(sessionId, messageId, time, local, sent, seen, read, authorId,
				name, accepted);

		this.message = message;
		this.answered = answered;
		this.exists = exists;
	}

	public String getMessage() {
		return message;
	}

	public boolean wasAnswered() {
		return answered;
	}

	public boolean doesExist() {
		return exists;
	}

}
