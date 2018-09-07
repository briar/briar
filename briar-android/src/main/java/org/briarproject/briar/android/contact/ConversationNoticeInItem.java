package org.briarproject.briar.android.contact;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.messaging.PrivateResponse;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class ConversationNoticeInItem extends ConversationItem {

	@Nullable
	private final String msgText;

	ConversationNoticeInItem(MessageId id, GroupId groupId,
			String text, @Nullable String msgText, long time,
			boolean read) {
		super(id, groupId, text, time, read);
		this.msgText = msgText;
	}

	public ConversationNoticeInItem(Context ctx, String contactName,
			PrivateResponse r) {
		super(r.getId(), r.getGroupId(), getText(ctx, contactName, r),
				r.getTimestamp(), r.isRead());
		this.msgText = null;
	}

	@Nullable
	String getMsgText() {
		return msgText;
	}

	@Override
	public boolean isIncoming() {
		return true;
	}

	@LayoutRes
	@Override
	public int getLayout() {
		return R.layout.list_item_conversation_notice_in;
	}

	private static String getText(Context ctx, String contactName,
			PrivateResponse r) {
		if (r.wasAccepted()) {
			if (r instanceof IntroductionResponse) {
				IntroductionResponse ir = (IntroductionResponse) r;
				return ctx.getString(
						R.string.introduction_response_accepted_received,
						contactName, ir.getIntroducedAuthor().getName());
			} else if (r instanceof ForumInvitationResponse) {
				return ctx.getString(
						R.string.forum_invitation_response_accepted_received,
						contactName);
			} else if (r instanceof BlogInvitationResponse) {
				return ctx.getString(
						R.string.blogs_sharing_response_accepted_received,
						contactName);
			} else if (r instanceof GroupInvitationResponse) {
				return ctx.getString(
						R.string.groups_invitations_response_accepted_received,
						contactName);
			}
		} else {
			if (r instanceof IntroductionResponse) {
				IntroductionResponse ir = (IntroductionResponse) r;
				@StringRes int res;
				if (ir.isIntroducer()) {
					res = R.string.introduction_response_declined_received;
				} else {
					res =
							R.string.introduction_response_declined_received_by_introducee;
				}
				return ctx.getString(res, contactName,
						ir.getIntroducedAuthor().getName());
			} else if (r instanceof ForumInvitationResponse) {
				return ctx.getString(
						R.string.forum_invitation_response_declined_received,
						contactName);
			} else if (r instanceof BlogInvitationResponse) {
				return ctx.getString(
						R.string.blogs_sharing_response_declined_received,
						contactName);
			} else if (r instanceof GroupInvitationResponse) {
				return ctx.getString(
						R.string.groups_invitations_response_declined_received,
						contactName);
			}
		}
		throw new IllegalArgumentException("Unknown PrivateResponse");
	}

}
