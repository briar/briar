package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.Role;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
abstract class Session<S extends State> {

	private final SessionId sessionId;
	private final S state;
	private final long requestTimestamp;

	Session(SessionId sessionId, S state, long requestTimestamp) {
		this.sessionId = sessionId;
		this.state = state;
		this.requestTimestamp = requestTimestamp;
	}

	abstract Role getRole();

	public SessionId getSessionId() {
		return sessionId;
	}

	S getState() {
		return state;
	}

	long getRequestTimestamp() {
		return requestTimestamp;
	}

}
