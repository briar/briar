package org.briarproject.briar.android.contact;

import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationRequestItem extends ConversationNoticeInItem {

	enum RequestType { INTRODUCTION, FORUM, BLOG, GROUP }

	@Nullable
	private final GroupId requestedGroupId;
	private final RequestType requestType;
	private final SessionId sessionId;
	private final boolean canBeOpened;
	private boolean answered;

	ConversationRequestItem(MessageId id, GroupId groupId,
			RequestType requestType, SessionId sessionId, String text,
			@Nullable String msgText, long time, boolean read,
			@Nullable GroupId requestedGroupId, boolean answered,
			boolean canBeOpened) {
		super(id, groupId, text, msgText, time, read);
		this.requestType = requestType;
		this.sessionId = sessionId;
		this.requestedGroupId = requestedGroupId;
		this.answered = answered;
		this.canBeOpened = canBeOpened;
	}

	RequestType getRequestType() {
		return requestType;
	}

	SessionId getSessionId() {
		return sessionId;
	}

	@Nullable
	public GroupId getRequestedGroupId() {
		return requestedGroupId;
	}

	boolean wasAnswered() {
		return answered;
	}

	void setAnswered(boolean answered) {
		this.answered = answered;
	}

	public boolean canBeOpened() {
		return canBeOpened;
	}

	@LayoutRes
	@Override
	public int getLayout() {
		return R.layout.list_item_conversation_request;
	}

}
