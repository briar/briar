package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionRequest extends IntroductionMessage {

	private final Author introducedAuthor;
	@Nullable
	private final String message;
	private final boolean answered, exists;

	public IntroductionRequest(SessionId sessionId, MessageId messageId,
			GroupId groupId, long time, boolean local, boolean sent,
			boolean seen, boolean read, Author introducedAuthor,
			@Nullable String message, boolean answered, boolean exists) {
		super(sessionId, messageId, groupId, time, local, sent, seen, read);
		this.introducedAuthor = introducedAuthor;
		this.message = message;
		this.answered = answered;
		this.exists = exists;
	}

	public String getName() {
		return introducedAuthor.getName();
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
}
