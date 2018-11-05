package org.briarproject.briar.android.conversation;

import android.arch.lifecycle.LiveData;
import android.content.Context;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.api.blog.BlogInvitationRequest;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.api.conversation.ConversationMessageVisitor;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;

import javax.annotation.Nullable;

import static org.briarproject.briar.android.conversation.ConversationRequestItem.RequestType.BLOG;
import static org.briarproject.briar.android.conversation.ConversationRequestItem.RequestType.FORUM;
import static org.briarproject.briar.android.conversation.ConversationRequestItem.RequestType.GROUP;
import static org.briarproject.briar.android.conversation.ConversationRequestItem.RequestType.INTRODUCTION;
import static org.briarproject.briar.android.util.UiUtils.getContactDisplayName;

@UiThread
@NotNullByDefault
class ConversationVisitor implements
		ConversationMessageVisitor<ConversationItem> {

	private final Context ctx;
	private final TextCache textCache;
	private final LiveData<String> contactName;

	ConversationVisitor(Context ctx, TextCache textCache,
			LiveData<String> contactName) {
		this.ctx = ctx;
		this.textCache = textCache;
		this.contactName = contactName;
	}

	@Override
	public ConversationItem visitPrivateMessageHeader(PrivateMessageHeader h) {
		ConversationItem item;
		if (h.isLocal()) item = new ConversationMessageOutItem(h);
		else item = new ConversationMessageInItem(h);
		String text = textCache.getText(h.getId());
		if (text != null) item.setText(text);
		return item;
	}

	@Override
	public ConversationItem visitBlogInvitationRequest(
			BlogInvitationRequest r) {
		if (r.isLocal()) {
			String text = ctx.getString(R.string.blogs_sharing_invitation_sent,
					r.getName(), contactName.getValue());
			return new ConversationNoticeOutItem(text, r);
		} else {
			String text = ctx.getString(
					R.string.blogs_sharing_invitation_received,
					contactName.getValue(), r.getName());
			return new ConversationRequestItem(text, BLOG, r);
		}
	}

	@Override
	public ConversationItem visitBlogInvitationResponse(
			BlogInvitationResponse r) {
		if (r.isLocal()) {
			String text;
			if (r.wasAccepted()) {
				text = ctx.getString(
						R.string.blogs_sharing_response_accepted_sent,
						contactName.getValue());
			} else {
				text = ctx.getString(
						R.string.blogs_sharing_response_declined_sent,
						contactName.getValue());
			}
			return new ConversationNoticeOutItem(text, r);
		} else {
			String text;
			if (r.wasAccepted()) {
				text = ctx.getString(
						R.string.blogs_sharing_response_accepted_received,
						contactName.getValue());
			} else {
				text = ctx.getString(
						R.string.blogs_sharing_response_declined_received,
						contactName.getValue());
			}
			return new ConversationNoticeInItem(text, r);
		}
	}

	@Override
	public ConversationItem visitForumInvitationRequest(
			ForumInvitationRequest r) {
		if (r.isLocal()) {
			String text = ctx.getString(R.string.forum_invitation_sent,
					r.getName(), contactName.getValue());
			return new ConversationNoticeOutItem(text, r);
		} else {
			String text = ctx.getString(
					R.string.forum_invitation_received,
					contactName.getValue(), r.getName());
			return new ConversationRequestItem(text, FORUM, r);
		}
	}

	@Override
	public ConversationItem visitForumInvitationResponse(
			ForumInvitationResponse r) {
		if (r.isLocal()) {
			String text;
			if (r.wasAccepted()) {
				text = ctx.getString(
						R.string.forum_invitation_response_accepted_sent,
						contactName.getValue());
			} else {
				text = ctx.getString(
						R.string.forum_invitation_response_declined_sent,
						contactName.getValue());
			}
			return new ConversationNoticeOutItem(text, r);
		} else {
			String text;
			if (r.wasAccepted()) {
				text = ctx.getString(
						R.string.forum_invitation_response_accepted_received,
						contactName.getValue());
			} else {
				text = ctx.getString(
						R.string.forum_invitation_response_declined_received,
						contactName.getValue());
			}
			return new ConversationNoticeInItem(text, r);
		}
	}

	@Override
	public ConversationItem visitGroupInvitationRequest(
			GroupInvitationRequest r) {
		if (r.isLocal()) {
			String text = ctx.getString(
					R.string.groups_invitations_invitation_sent,
					contactName.getValue(), r.getName());
			return new ConversationNoticeOutItem(text, r);
		} else {
			String text = ctx.getString(
					R.string.groups_invitations_invitation_received,
					contactName.getValue(), r.getName());
			return new ConversationRequestItem(text, GROUP, r);
		}
	}

	@Override
	public ConversationItem visitGroupInvitationResponse(
			GroupInvitationResponse r) {
		if (r.isLocal()) {
			String text;
			if (r.wasAccepted()) {
				text = ctx.getString(
						R.string.groups_invitations_response_accepted_sent,
						contactName.getValue());
			} else {
				text = ctx.getString(
						R.string.groups_invitations_response_declined_sent,
						contactName.getValue());
			}
			return new ConversationNoticeOutItem(text, r);
		} else {
			String text;
			if (r.wasAccepted()) {
				text = ctx.getString(
						R.string.groups_invitations_response_accepted_received,
						contactName.getValue());
			} else {
				text = ctx.getString(
						R.string.groups_invitations_response_declined_received,
						contactName.getValue());
			}
			return new ConversationNoticeInItem(text, r);
		}
	}

	@Override
	public ConversationItem visitIntroductionRequest(IntroductionRequest r) {
		String name = getContactDisplayName(r.getNameable(), r.getAlias());
		if (r.isLocal()) {
			String text = ctx.getString(R.string.introduction_request_sent,
					contactName.getValue(), name);
			return new ConversationNoticeOutItem(text, r);
		} else {
			String text = ctx.getString(R.string.introduction_request_received,
					contactName.getValue(), name);
			return new ConversationRequestItem(text, INTRODUCTION, r);
		}
	}

	@Override
	public ConversationItem visitIntroductionResponse(IntroductionResponse r) {
		String introducedAuthor =
				getContactDisplayName(r.getIntroducedAuthor(),
						r.getIntroducedAuthorInfo().getAlias());
		if (r.isLocal()) {
			String text;
			if (r.wasAccepted()) {
				text = ctx.getString(
						R.string.introduction_response_accepted_sent,
						introducedAuthor)
						+ "\n\n" + ctx.getString(
						R.string.introduction_response_accepted_sent_info,
						introducedAuthor);
			} else {
				text = ctx.getString(
						R.string.introduction_response_declined_sent,
						introducedAuthor);
			}
			return new ConversationNoticeOutItem(text, r);
		} else {
			String text;
			if (r.wasAccepted()) {
				text = ctx.getString(
						R.string.introduction_response_accepted_received,
						contactName.getValue(),
						introducedAuthor);
			} else if (r.isIntroducer()) {
				text = ctx.getString(
						R.string.introduction_response_declined_received,
						contactName.getValue(),
						introducedAuthor);
			} else {
				text = ctx.getString(
						R.string.introduction_response_declined_received_by_introducee,
						contactName.getValue(),
						introducedAuthor);
			}
			return new ConversationNoticeInItem(text, r);
		}
	}

	interface TextCache {
		@Nullable
		String getText(MessageId m);
	}
}
