package org.briarproject.briar.android.contact;

import android.content.Context;
import android.support.annotation.LayoutRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.api.blog.BlogInvitationRequest;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.messaging.PrivateResponse;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationNoticeOutItem extends ConversationOutItem {

	@Nullable
	private final String msgText;

	ConversationNoticeOutItem(Context ctx, String contactName,
			PrivateRequest r) {
		super(r.getId(), r.getGroupId(), getText(ctx, contactName, r),
				r.getTimestamp(), r.isSent(), r.isSeen());
		this.msgText = r.getMessage();
	}

	ConversationNoticeOutItem(Context ctx, String contactName,
			PrivateResponse r) {
		super(r.getId(), r.getGroupId(), getText(ctx, contactName, r),
				r.getTimestamp(), r.isSent(), r.isSeen());
		this.msgText = null;
	}

	@Nullable
	String getMsgText() {
		return msgText;
	}

	@LayoutRes
	@Override
	public int getLayout() {
		return R.layout.list_item_conversation_notice_out;
	}

	private static String getText(Context ctx, String contactName,
			PrivateRequest r) {
		if (r instanceof IntroductionRequest) {
			return ctx.getString(R.string.introduction_request_sent,
					contactName, r.getName());
		} else if (r instanceof ForumInvitationRequest) {
			return ctx.getString(R.string.forum_invitation_sent,
					r.getName(), contactName);
		} else if (r instanceof BlogInvitationRequest) {
			return ctx.getString(R.string.blogs_sharing_invitation_sent,
					r.getName(), contactName);
		} else if (r instanceof GroupInvitationRequest) {
			return ctx.getString(R.string.groups_invitations_invitation_sent,
					contactName, r.getName());
		}
		throw new IllegalArgumentException("Unknown PrivateRequest");
	}

	private static String getText(Context ctx, String contactName,
			PrivateResponse r) {
		if (r.wasAccepted()) {
			if (r instanceof IntroductionResponse) {
				String name = ((IntroductionResponse) r).getIntroducedAuthor()
						.getName();
				return ctx.getString(
						R.string.introduction_response_accepted_sent,
						name) + "\n\n" + ctx.getString(
						R.string.introduction_response_accepted_sent_info,
						name);
			} else if (r instanceof ForumInvitationResponse) {
				return ctx.getString(R.string.forum_invitation_response_accepted_sent, contactName);
			} else if (r instanceof BlogInvitationResponse) {
				return ctx.getString(R.string.blogs_sharing_response_accepted_sent, contactName);
			} else if (r instanceof GroupInvitationResponse) {
				return ctx.getString(R.string.groups_invitations_response_accepted_sent, contactName);
			}
		} else {
			if (r instanceof IntroductionResponse) {
				String name = ((IntroductionResponse) r).getIntroducedAuthor()
						.getName();
				return ctx.getString(
						R.string.introduction_response_declined_sent, name);
			} else if (r instanceof ForumInvitationResponse) {
				return ctx.getString(R.string.forum_invitation_response_declined_sent, contactName);
			} else if (r instanceof BlogInvitationResponse) {
				return ctx.getString(R.string.blogs_sharing_response_declined_sent, contactName);
			} else if (r instanceof GroupInvitationResponse) {
				return ctx.getString(R.string.groups_invitations_response_declined_sent, contactName);
			}
		}
		throw new IllegalArgumentException("Unknown PrivateResponse");
	}

}
