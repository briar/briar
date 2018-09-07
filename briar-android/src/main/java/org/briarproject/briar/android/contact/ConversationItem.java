package org.briarproject.briar.android.contact;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.ConversationRequestItem.RequestType;
import org.briarproject.briar.api.blog.BlogInvitationRequest;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.api.messaging.PrivateRequest;
import org.briarproject.briar.api.messaging.PrivateResponse;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;
import org.briarproject.briar.api.sharing.Shareable;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.briar.android.contact.ConversationRequestItem.RequestType.BLOG;
import static org.briarproject.briar.android.contact.ConversationRequestItem.RequestType.FORUM;
import static org.briarproject.briar.android.contact.ConversationRequestItem.RequestType.GROUP;
import static org.briarproject.briar.android.contact.ConversationRequestItem.RequestType.INTRODUCTION;

@NotThreadSafe
@NotNullByDefault
abstract class ConversationItem {

	@Nullable
	protected String body;
	private final MessageId id;
	private final GroupId groupId;
	private final long time;
	private boolean read;

	ConversationItem(MessageId id, GroupId groupId, @Nullable String body,
			long time, boolean read) {
		this.id = id;
		this.groupId = groupId;
		this.body = body;
		this.time = time;
		this.read = read;
	}

	MessageId getId() {
		return id;
	}

	GroupId getGroupId() {
		return groupId;
	}

	void setBody(String body) {
		this.body = body;
	}

	@Nullable
	public String getBody() {
		return body;
	}

	long getTime() {
		return time;
	}

	public boolean isRead() {
		return read;
	}

	abstract public boolean isIncoming();

	@LayoutRes
	abstract public int getLayout();

	static ConversationItem from(PrivateMessageHeader h) {
		if (h.isLocal()) {
			return new ConversationMessageOutItem(h);
		} else {
			return new ConversationMessageInItem(h);
		}
	}

	static ConversationItem from(Context ctx, String contactName,
			PrivateRequest ir) {
		if (ir.isLocal()) {
			String text;
			if (ir instanceof IntroductionRequest) {
				text = ctx.getString(R.string.introduction_request_sent,
						contactName, ir.getName());
			} else if (ir instanceof ForumInvitationRequest) {
				text = ctx.getString(R.string.forum_invitation_sent,
						ir.getName(), contactName);
			} else if (ir instanceof BlogInvitationRequest) {
				text = ctx.getString(R.string.blogs_sharing_invitation_sent,
						ir.getName(), contactName);
			} else if (ir instanceof GroupInvitationRequest) {
				text = ctx.getString(
						R.string.groups_invitations_invitation_sent,
						contactName, ir.getName());
			} else {
				throw new IllegalArgumentException("Unknown PrivateRequest");
			}
			return new ConversationNoticeOutItem(ir.getId(), ir.getGroupId(),
					text, ir.getMessage(), ir.getTimestamp(), ir.isSent(),
					ir.isSeen());
		} else {
			String text;
			RequestType type;
			boolean canBeOpened;
			if (ir instanceof IntroductionRequest) {
				type = INTRODUCTION;
				if (ir.wasAnswered()) {
					text = ctx.getString(
							R.string.introduction_request_answered_received,
							contactName, ir.getName());
				} else if (((IntroductionRequest) ir).isContact()) {
					text = ctx.getString(
							R.string.introduction_request_exists_received,
							contactName, ir.getName());
				} else {
					text = ctx.getString(R.string.introduction_request_received,
							contactName, ir.getName());
				}
				return new ConversationRequestItem(ir.getId(),
						ir.getGroupId(), type, ir.getSessionId(), text,
						ir.getMessage(), ir.getTimestamp(), ir.isRead(), null,
						ir.wasAnswered(), false);
			} else if (ir instanceof ForumInvitationRequest) {
				text = ctx.getString(R.string.forum_invitation_received,
						contactName, ir.getName());
				type = FORUM;
				canBeOpened = ((ForumInvitationRequest) ir).canBeOpened();
			} else if (ir instanceof BlogInvitationRequest) {
				text = ctx.getString(R.string.blogs_sharing_invitation_received,
						contactName, ir.getName());
				type = BLOG;
				canBeOpened = ((BlogInvitationRequest) ir).canBeOpened();
			} else if (ir instanceof GroupInvitationRequest) {
				text = ctx.getString(
						R.string.groups_invitations_invitation_received,
						contactName, ir.getName());
				type = GROUP;
				canBeOpened = ((GroupInvitationRequest) ir).canBeOpened();
			} else {
				throw new IllegalArgumentException("Unknown PrivateRequest");
			}
			return new ConversationRequestItem(ir.getId(),
					ir.getGroupId(), type, ir.getSessionId(), text,
					ir.getMessage(), ir.getTimestamp(), ir.isRead(),
					((Shareable) ir.getNameable()).getId(), !ir.wasAnswered(),
					canBeOpened);
		}
	}

	static ConversationItem from(Context ctx, String contactName,
			IntroductionResponse ir) {
		if (ir.isLocal()) {
			String text;
			if (ir.wasAccepted()) {
				text = ctx.getString(
						R.string.introduction_response_accepted_sent,
						ir.getNameable().getName());
				text += "\n\n" + ctx.getString(
						R.string.introduction_response_accepted_sent_info,
						ir.getNameable().getName());
			} else {
				text = ctx.getString(
						R.string.introduction_response_declined_sent,
						ir.getNameable().getName());
			}
			return new ConversationNoticeOutItem(ir.getId(), ir.getGroupId(),
					text, null, ir.getTimestamp(), ir.isSent(), ir.isSeen());
		} else {
			@StringRes int res;
			if (ir.wasAccepted()) {
				res = R.string.introduction_response_accepted_received;
			} else {
				if (ir.isIntroducer()) {
					res = R.string.introduction_response_declined_received;
				} else {
					res =
							R.string.introduction_response_declined_received_by_introducee;
				}
			}
			String text =
					ctx.getString(res, contactName, ir.getNameable().getName());
			return new ConversationNoticeInItem(ir.getId(), ir.getGroupId(),
					text, null, ir.getTimestamp(), ir.isRead());
		}
	}

	static ConversationItem from(Context ctx, String contactName,
			PrivateResponse ir) {
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
							"Unknown PrivateResponse");
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
							"Unknown PrivateResponse");
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
					res =
							R.string.groups_invitations_response_accepted_received;
				} else {
					throw new IllegalArgumentException(
							"Unknown PrivateResponse");
				}
			} else {
				if (ir instanceof ForumInvitationResponse) {
					res = R.string.forum_invitation_response_declined_received;
				} else if (ir instanceof BlogInvitationResponse) {
					res = R.string.blogs_sharing_response_declined_received;
				} else if (ir instanceof GroupInvitationResponse) {
					res =
							R.string.groups_invitations_response_declined_received;
				} else {
					throw new IllegalArgumentException(
							"Unknown PrivateResponse");
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
	 * PrivateMessageHeader.
	 **/
	static ConversationItem from(Context ctx, PrivateMessageHeader h) {
		if (h instanceof IntroductionResponse) {
			return from(ctx, "", (IntroductionResponse) h);
		} else if (h instanceof PrivateRequest) {
			return from(ctx, "", (PrivateRequest) h);
		} else if (h instanceof PrivateResponse) {
			return from(ctx, "", (PrivateResponse) h);
		} else {
			return from(h);
		}
	}

}
