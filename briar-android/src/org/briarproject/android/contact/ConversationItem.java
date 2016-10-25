package org.briarproject.android.contact;

import android.content.Context;
import android.support.annotation.StringRes;

import org.briarproject.R;
import org.briarproject.android.contact.ConversationRequestItem.RequestType;
import org.briarproject.api.blogs.BlogInvitationRequest;
import org.briarproject.api.blogs.BlogInvitationResponse;
import org.briarproject.api.clients.BaseMessageHeader;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.forum.ForumInvitationResponse;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.api.privategroup.invitation.GroupInvitationResponse;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sharing.InvitationResponse;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.android.contact.ConversationRequestItem.RequestType.BLOG;
import static org.briarproject.android.contact.ConversationRequestItem.RequestType.FORUM;
import static org.briarproject.android.contact.ConversationRequestItem.RequestType.GROUP;
import static org.briarproject.android.contact.ConversationRequestItem.RequestType.INTRODUCTION;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationItem {

	protected @Nullable String text;
	final private MessageId id;
	final private GroupId groupId;
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
			} else if (ir instanceof BlogInvitationRequest) {
				text = ctx.getString(R.string.blogs_sharing_invitation_sent,
						((BlogInvitationRequest) ir).getBlogAuthorName(),
						contactName);
			} else if (ir instanceof GroupInvitationRequest) {
				text = ctx.getString(
						R.string.groups_invitations_invitation_sent,
						contactName,
						((GroupInvitationRequest) ir).getGroupName());
			} else {
				throw new IllegalArgumentException("Unknown InvitationRequest");
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
			} else if (ir instanceof BlogInvitationRequest) {
				text = ctx.getString(R.string.blogs_sharing_invitation_received,
						contactName,
						((BlogInvitationRequest) ir).getBlogAuthorName());
				type = BLOG;
			} else if (ir instanceof GroupInvitationRequest) {
				text = ctx.getString(
						R.string.groups_invitations_invitation_received,
						contactName,
						((GroupInvitationRequest) ir).getGroupName());
				type = GROUP;
			} else {
				throw new IllegalArgumentException("Unknown InvitationRequest");
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
				} else if (ir instanceof BlogInvitationResponse) {
					res = R.string.blogs_sharing_response_accepted_sent;
				} else if (ir instanceof GroupInvitationResponse) {
					res = R.string.groups_invitations_response_accepted_sent;
				} else {
					throw new IllegalArgumentException(
							"Unknown InvitationResponse");
				}
			} else {
				if (ir instanceof ForumInvitationResponse) {
					res = R.string.forum_invitation_response_declined_sent;
				} else if (ir instanceof BlogInvitationResponse) {
					res = R.string.blogs_sharing_response_declined_sent;
				} else if (ir instanceof GroupInvitationResponse) {
					res = R.string.groups_invitations_response_declined_sent;
				} else {
					throw new IllegalArgumentException(
							"Unknown InvitationResponse");
				}
			}
			String text = ctx.getString(res, contactName);
			return new ConversationNoticeOutItem(ir.getId(), ir.getGroupId(),
					text, null, ir.getTimestamp(), ir.isSent(), ir.isSeen());
		} else {
			if (ir.wasAccepted()) {
				if (ir instanceof ForumInvitationResponse) {
					res = R.string.forum_invitation_response_accepted_received;
				} else if (ir instanceof BlogInvitationResponse) {
					res = R.string.blogs_sharing_response_accepted_received;
				} else if (ir instanceof GroupInvitationResponse) {
					res = R.string.groups_invitations_response_accepted_received;
				} else {
					throw new IllegalArgumentException(
							"Unknown InvitationResponse");
				}
			} else {
					if (ir instanceof ForumInvitationResponse) {
						res = R.string.forum_invitation_response_declined_received;
					} else if (ir instanceof BlogInvitationResponse) {
						res = R.string.blogs_sharing_response_declined_received;
					} else if (ir instanceof GroupInvitationResponse) {
						res = R.string.groups_invitations_response_declined_received;
					} else {
						throw new IllegalArgumentException(
								"Unknown InvitationResponse");
					}
			}
			String text = ctx.getString(res, contactName);
			return new ConversationNoticeInItem(ir.getId(), ir.getGroupId(),
					text, null, ir.getTimestamp(), ir.isRead());
		}
	}

	/**
	 * This method should not be used to display the resulting ConversationItem
	 * in the UI, but only to update list information based on the
	 * BaseMessageHeader.
	 **/
	static ConversationItem from(Context ctx, BaseMessageHeader h) {
		if (h instanceof PrivateMessageHeader) {
			return from((PrivateMessageHeader) h);
		} else if(h instanceof IntroductionRequest) {
			return from(ctx, "", (IntroductionRequest) h);
		} else if(h instanceof IntroductionResponse) {
			return from(ctx, "", (IntroductionResponse) h);
		} else if(h instanceof InvitationRequest) {
			return from(ctx, "", (InvitationRequest) h);
		} else if(h instanceof InvitationResponse) {
			return from(ctx, "", (InvitationResponse) h);
		} else {
			throw new IllegalArgumentException("Unknown message header");
		}
	}

	interface PartialItem {

		@Nullable
		String getText();

		void setText(String text);

	}

}
