package org.briarproject.briar.android.contact;

import android.content.Context;
import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.api.blog.BlogInvitationRequest;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.sharing.InvitationRequest;
import org.briarproject.briar.api.sharing.Shareable;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.briar.android.contact.ConversationRequestItem.RequestType.BLOG;
import static org.briarproject.briar.android.contact.ConversationRequestItem.RequestType.FORUM;
import static org.briarproject.briar.android.contact.ConversationRequestItem.RequestType.GROUP;
import static org.briarproject.briar.android.contact.ConversationRequestItem.RequestType.INTRODUCTION;

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

	ConversationRequestItem(Context ctx, String contactName,
			PrivateRequest r) {
		super(r.getId(), r.getGroupId(), getText(ctx, contactName, r),
				r.getMessage(), r.getTimestamp(), r.isRead());
		this.requestType = getType(r);
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

	private static String getText(Context ctx, String contactName,
			PrivateRequest r) {
		if (r instanceof IntroductionRequest) {
			if (r.wasAnswered()) {
				return ctx.getString(
						R.string.introduction_request_answered_received,
						contactName, r.getName());
			} else if (((IntroductionRequest) r).isContact()) {
				return ctx.getString(
						R.string.introduction_request_exists_received,
						contactName, r.getName());
			} else {
				return ctx.getString(R.string.introduction_request_received,
						contactName, r.getName());
			}
		} else if (r instanceof ForumInvitationRequest) {
			return ctx.getString(R.string.forum_invitation_received,
					contactName, r.getName());
		} else if (r instanceof BlogInvitationRequest) {
			return ctx.getString(R.string.blogs_sharing_invitation_received,
					contactName, r.getName());
		} else if (r instanceof GroupInvitationRequest) {
			return ctx.getString(
					R.string.groups_invitations_invitation_received,
					contactName, r.getName());
		}
		throw new IllegalArgumentException("Unknown PrivateRequest");
	}

	private static RequestType getType(PrivateRequest r) {
		if (r instanceof IntroductionRequest) {
			return INTRODUCTION;
		} else if (r instanceof ForumInvitationRequest) {
			return FORUM;
		} else if (r instanceof BlogInvitationRequest) {
			return BLOG;
		} else if (r instanceof GroupInvitationRequest) {
			return GROUP;
		}
		throw new IllegalArgumentException("Unknown PrivateRequest");
	}

}
