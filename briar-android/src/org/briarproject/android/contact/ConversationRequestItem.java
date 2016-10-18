package org.briarproject.android.contact;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationRequestItem extends ConversationNoticeInItem {

	enum RequestType { INTRODUCTION, FORUM, BLOG, GROUP };
	private final RequestType requestType;
	private final SessionId sessionId;
	private boolean answered;

	ConversationRequestItem(MessageId id, GroupId groupId,
			RequestType requestType, SessionId sessionId, String text,
			@Nullable String msgText, long time, boolean read,
			boolean answered) {
		super(id, groupId, text, msgText, time, read);
		this.requestType = requestType;
		this.sessionId = sessionId;
		this.answered = answered;
	}

	public RequestType getRequestType() {
		return requestType;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	boolean wasAnswered() {
		return answered;
	}

	void setAnswered(boolean answered) {
		this.answered = answered;
	}

}
