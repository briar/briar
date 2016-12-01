package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionRequest extends IntroductionResponse {

	@Nullable
	private final String message;
	private final boolean answered, exists, introducesOtherIdentity;

	public IntroductionRequest(SessionId sessionId, MessageId messageId,
			GroupId groupId, int role, long time, boolean local, boolean sent,
			boolean seen, boolean read, AuthorId authorId, String name,
			boolean accepted, @Nullable String message, boolean answered,
			boolean exists, boolean introducesOtherIdentity) {

		super(sessionId, messageId, groupId, role, time, local, sent, seen,
				read, authorId, name, accepted);

		this.message = message;
		this.answered = answered;
		this.exists = exists;
		this.introducesOtherIdentity = introducesOtherIdentity;
	}

	@Nullable
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
