package org.briarproject.api.introduction;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntroductionRequest extends IntroductionResponse {

	private final String message;
	private final boolean answered, exists, introducesOtherIdentity;

	public IntroductionRequest(@NotNull SessionId sessionId,
			@NotNull MessageId messageId, @NotNull GroupId groupId, int role,
			long time, boolean local, boolean sent, boolean seen, boolean read,
			AuthorId authorId, String name, boolean accepted,
			@Nullable String message, boolean answered, boolean exists,
			boolean introducesOtherIdentity) {

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
