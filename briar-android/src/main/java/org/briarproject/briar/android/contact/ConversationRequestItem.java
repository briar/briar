package org.briarproject.briar.android.contact;

import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.sharing.InvitationRequest;
import org.briarproject.briar.api.sharing.Shareable;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationRequestItem extends ConversationNoticeInItem {

	enum RequestType {INTRODUCTION, FORUM, BLOG, GROUP}

	@Nullable
	private final GroupId requestedGroupId;
	private final RequestType requestType;
	private final SessionId sessionId;
	private final boolean canBeOpened;
	private boolean answered;

	ConversationRequestItem(String text, RequestType type, PrivateRequest r) {
		super(r.getId(), r.getGroupId(), text, r.getText(),
				r.getTimestamp(), r.isRead());
		this.requestType = type;
		this.sessionId = r.getSessionId();
		this.answered = r.wasAnswered();
		if (r instanceof InvitationRequest) {
			this.requestedGroupId = ((Shareable) r.getNameable()).getId();
			this.canBeOpened = ((InvitationRequest) r).canBeOpened();
		} else {
			this.requestedGroupId = null;
			this.canBeOpened = false;
		}
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

	void setAnswered() {
		this.answered = true;
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
