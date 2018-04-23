package org.briarproject.briar.api.introduction2.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
// TODO still needed?
public class IntroductionAbortedEvent extends Event {

	private final AuthorId remoteAuthorId;
	private final SessionId sessionId;

	public IntroductionAbortedEvent(AuthorId remoteAuthorId,
			SessionId sessionId) {
		this.remoteAuthorId = remoteAuthorId;
		this.sessionId = sessionId;
	}

	public AuthorId getRemoteAuthorId() {
		return remoteAuthorId;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

}
