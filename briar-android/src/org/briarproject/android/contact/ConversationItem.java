package org.briarproject.android.contact;

import android.content.Context;
import android.support.annotation.StringRes;

import org.briarproject.R;
import org.briarproject.android.contact.ConversationRequestItem.RequestType;
import org.briarproject.api.blogs.BlogInvitationRequest;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.forum.ForumInvitationResponse;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sharing.InvitationResponse;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.android.contact.ConversationRequestItem.RequestType.BLOG;
import static org.briarproject.android.contact.ConversationRequestItem.RequestType.FORUM;
import static org.briarproject.android.contact.ConversationRequestItem.RequestType.INTRODUCTION;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationItem {

	final private MessageId id;
	final private GroupId groupId;
	protected @Nullable String text;
	final private long time;

	ConversationItem(MessageId id, GroupId groupId,
			@Nullable String text, long time) {
		this.id = id;
		this.groupId = groupId;
		this.text = text;
		this.time = time;
	}

	MessageId getId() {
		return id;
	}

	GroupId getGroupId() {
		return groupId;
	}

	@Nullable
	public String getText() {
		return text;
	}

	long getTime() {
		return time;
	}

	static ConversationItem from(PrivateMessageHeader h) {
		if (h.isLocal()) {
			return new ConversationMessageOutItem(h);
		} else {
			return new ConversationMessageInItem(h);
		}
	}

	static ConversationItem from(Context ctx, String contactName,
			IntroductionRequest ir) {
		if (ir.isLocal()) {
			String text = ctx.getString(R.string.introduction_request_sent,
					contactName, ir.getName());
			return new ConversationNoticeOutItem(ir.getMessageId(),
					ir.getGroupId(), text, ir.getMessage(), ir.getTimestamp(),
					ir.isSent(), ir.isSeen());
		} else {
			String text;
			if (ir.wasAnswered()) {
				text = ctx.getString(
						R.string.introduction_request_answered_received,
						contactName, ir.getName());
				return new ConversationNoticeInItem(ir.getMessageId(),
						ir.getGroupId(), text, ir.getMessage(), ir.getTimestamp(),
						ir.isRead());
			} else if (ir.contactExists()){
				text = ctx.getString(
						R.string.introduction_request_exists_received,
						contactName, ir.getName());
			} else {
				text = ctx.getString(R.string.introduction_request_received,
						contactName, ir.getName());
			}
			return new ConversationRequestItem(ir.getMessageId(),
					ir.getGroupId(), INTRODUCTION, ir.getSessionId(), text,
					ir.getMessage(), ir.getTimestamp(), ir.isRead(),
					ir.wasAnswered());
		}
	}

	static ConversationItem from(Context ctx, String contactName,
			IntroductionResponse ir) {
		if (ir.isLocal()) {
			String text;
			if (ir.wasAccepted()) {
				text = ctx.getString(
						R.string.introduction_response_accepted_sent,
						ir.getName());
			} else {
				text = ctx.getString(
						R.string.introduction_response_declined_sent,
						ir.getName());
			}
			return new ConversationNoticeOutItem(ir.getMessageId(),
					ir.getGroupId(), text, null, ir.getTimestamp(), ir.isSent(),
					ir.isSeen());
		} else {
			String text;
			if (ir.wasAccepted()) {
				text = ctx.getString(
						R.string.introduction_response_accepted_received,
						contactName, ir.getName());
			} else {
				if (ir.isIntroducer()) {
					text = ctx.getString(
							R.string.introduction_response_declined_received,
							contactName, ir.getName());
				} else {
					text = ctx.getString(
							R.string.introduction_response_declined_received_by_introducee,
							contactName, ir.getName());
				}
			}
			return new ConversationNoticeInItem(ir.getMessageId(),
					ir.getGroupId(), text, null, ir.getTimestamp(),
					ir.isRead());
		}
	}

	static ConversationItem from(Context ctx, String contactName,
			InvitationRequest ir) {
		if (ir.isLocal()) {
			String text;
			if (ir instanceof ForumInvitationRequest) {
				text = ctx.getString(R.string.forum_invitation_sent,
						((ForumInvitationRequest) ir).getForumName(),
						contactName);
			} else {
				text = ctx.getString(R.string.blogs_sharing_invitation_sent,
						((BlogInvitationRequest) ir).getBlogAuthorName(),
						contactName);
			}
			return new ConversationNoticeOutItem(ir.getId(), ir.getGroupId(),
					text, ir.getMessage(), ir.getTimestamp(), ir.isSent(),
					ir.isSeen());
		} else {
			String text;
			RequestType type;
			if (ir instanceof ForumInvitationRequest) {
				text = ctx.getString(R.string.forum_invitation_received,
						contactName,
						((ForumInvitationRequest) ir).getForumName());
				type = FORUM;
			} else {
				text = ctx.getString(R.string.blogs_sharing_invitation_received,
						contactName,
						((BlogInvitationRequest) ir).getBlogAuthorName());
				type = BLOG;
			}
			if (!ir.isAvailable()) {
				return new ConversationNoticeInItem(ir.getId(), ir.getGroupId(),
						text, ir.getMessage(), ir.getTimestamp(), ir.isRead());
			}
			return new ConversationRequestItem(ir.getId(),
					ir.getGroupId(), type, ir.getSessionId(), text,
					ir.getMessage(), ir.getTimestamp(), ir.isRead(),
					!ir.isAvailable());
		}
	}

	static ConversationItem from(Context ctx, String contactName,
			InvitationResponse ir) {
		@StringRes int res;
		if (ir.isLocal()) {
			if (ir.wasAccepted()) {
				if (ir instanceof ForumInvitationResponse) {
					res = R.string.forum_invitation_response_accepted_sent;
				} else {
					res = R.string.blogs_sharing_response_accepted_sent;
				}
			} else {
				if (ir instanceof ForumInvitationResponse) {
					res = R.string.forum_invitation_response_declined_sent;
				} else {
					res = R.string.blogs_sharing_response_declined_sent;
				}
			}
			String text = ctx.getString(res, contactName);
			return new ConversationNoticeOutItem(ir.getId(), ir.getGroupId(),
					text, null, ir.getTimestamp(), ir.isSent(), ir.isSeen());
		} else {
			if (ir.wasAccepted()) {
				if (ir instanceof ForumInvitationResponse) {
					res = R.string.forum_invitation_response_accepted_received;
				} else {
					res = R.string.blogs_sharing_response_accepted_received;
				}
			} else {
					if (ir instanceof ForumInvitationResponse) {
						res = R.string.forum_invitation_response_declined_received;
					} else {
						res = R.string.blogs_sharing_response_declined_received;
					}
			}
			String text = ctx.getString(res, contactName);
			return new ConversationNoticeInItem(ir.getId(), ir.getGroupId(),
					text, null, ir.getTimestamp(), ir.isRead());
		}
	}

	interface PartialItem {

		@Nullable
		String getText();

		void setText(String text);

	}

}
