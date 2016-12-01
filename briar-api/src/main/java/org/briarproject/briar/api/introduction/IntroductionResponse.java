package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
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
