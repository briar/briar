package org.briarproject.android.contact;

import android.content.Context;

import org.briarproject.R;
import org.briarproject.api.blogs.BlogInvitationResponse;
import org.briarproject.api.forum.ForumInvitationResponse;
import org.briarproject.api.introduction.IntroductionMessage;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sharing.InvitationRequest;
import org.briarproject.api.sharing.InvitationResponse;
import org.briarproject.api.sync.MessageId;

// This class is not thread-safe
public abstract class ConversationItem {

	// this is needed for RecyclerView adapter which requires an int type
	final static int MSG_IN = 0;
	final static int MSG_IN_UNREAD = 1;
	final static int MSG_OUT = 2;
	final static int INTRODUCTION_IN = 3;
	final static int INTRODUCTION_OUT = 4;
	final static int NOTICE_IN = 5;
	final static int NOTICE_OUT = 6;
	final static int FORUM_INVITATION_IN = 7;
	final static int FORUM_INVITATION_OUT = 8;
	final static int BLOG_INVITATION_IN = 9;
	final static int BLOG_INVITATION_OUT = 10;

	private MessageId id;
	private long time;

	public ConversationItem(MessageId id, long time) {
		this.id = id;
		this.time = time;
	}

	abstract int getType();

	public MessageId getId() {
		return id;
	}

	long getTime() {
		return time;
	}

	public static ConversationMessageItem from(PrivateMessageHeader h) {
		if (h.isLocal()) {
			return new ConversationMessageOutItem(h);
		} else {
			return new ConversationMessageInItem(h);
		}
	}

	public static ConversationIntroductionItem from(IntroductionRequest ir) {
		if (ir.isLocal()) {
			return new ConversationIntroductionOutItem(ir);
		} else {
			return new ConversationIntroductionInItem(ir);
		}
	}

	public static ConversationNoticeItem from(Context ctx, String contactName,
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
			return new ConversationNoticeOutItem(ir.getMessageId(), text,
					ir.getTimestamp(), ir.isSent(), ir.isSeen());
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
			return new ConversationNoticeInItem(ir.getMessageId(), text,
					ir.getTimestamp(), ir.isRead());
		}
	}

	public static ConversationShareableInvitationItem from(
			InvitationRequest fim) {
		if (fim.isLocal()) {
			return new ConversationShareableInvitationOutItem(fim);
		} else {
			return new ConversationShareableInvitationInItem(fim);
		}
	}

	public static ConversationNoticeItem from(Context ctx, String contactName,
			InvitationResponse ir) {

		if (ir instanceof ForumInvitationResponse) {
			return from(ctx, contactName, (ForumInvitationResponse) ir);
		} else if (ir instanceof BlogInvitationResponse) {
			return from(ctx, contactName, (BlogInvitationResponse) ir);
		} else {
			throw new IllegalArgumentException("Unknown Invitation Response.");
		}
	}

	private static ConversationNoticeItem from(Context ctx, String contactName,
			ForumInvitationResponse fir) {

		if (fir.isLocal()) {
			String text;
			if (fir.wasAccepted()) {
				text = ctx.getString(
						R.string.forum_invitation_response_accepted_sent,
						contactName);
			} else {
				text = ctx.getString(
						R.string.forum_invitation_response_declined_sent,
						contactName);
			}
			return new ConversationNoticeOutItem(fir.getId(), text,
					fir.getTimestamp(), fir.isSent(), fir.isSeen());
		} else {
			String text;
			if (fir.wasAccepted()) {
				text = ctx.getString(
						R.string.forum_invitation_response_accepted_received,
						contactName);
			} else {
				text = ctx.getString(
						R.string.forum_invitation_response_declined_received,
						contactName);
			}
			return new ConversationNoticeInItem(fir.getId(), text,
					fir.getTimestamp(), fir.isRead());
		}
	}

	private static ConversationNoticeItem from(Context ctx, String contactName,
			BlogInvitationResponse fir) {

		if (fir.isLocal()) {
			String text;
			if (fir.wasAccepted()) {
				text = ctx.getString(
						R.string.blogs_sharing_response_accepted_sent,
						contactName);
			} else {
				text = ctx.getString(
						R.string.blogs_sharing_response_declined_sent,
						contactName);
			}
			return new ConversationNoticeOutItem(fir.getId(), text,
					fir.getTimestamp(), fir.isSent(), fir.isSeen());
		} else {
			String text;
			if (fir.wasAccepted()) {
				text = ctx.getString(
						R.string.blogs_sharing_response_accepted_received,
						contactName);
			} else {
				text = ctx.getString(
						R.string.blogs_sharing_response_declined_received,
						contactName);
			}
			return new ConversationNoticeInItem(fir.getId(), text,
					fir.getTimestamp(), fir.isRead());
		}
	}

	/**
	 * This method should not be used to get user-facing objects,
	 * Its purpose is only to provide data for the contact list.
	 */
	public static ConversationItem from(IntroductionMessage im) {
		if (im.isLocal())
			return new ConversationNoticeOutItem(im.getMessageId(), "",
					im.getTimestamp(), false, false);
		return new ConversationNoticeInItem(im.getMessageId(), "",
				im.getTimestamp(), im.isRead());
	}

	/**
	 * This method should not be used to get user-facing objects,
	 * Its purpose is only to provide data for the contact list.
	 */
	public static ConversationItem from(InvitationMessage im) {
		if (im.isLocal())
			return new ConversationNoticeOutItem(im.getId(), "",
					im.getTimestamp(), false, false);
		return new ConversationNoticeInItem(im.getId(), "",
				im.getTimestamp(), im.isRead());
	}

	interface OutgoingItem {

		MessageId getId();

		boolean isSent();

		void setSent(boolean sent);

		boolean isSeen();

		void setSeen(boolean seen);
	}

	interface IncomingItem {

		MessageId getId();

		boolean isRead();

		void setRead(boolean read);
	}
}
