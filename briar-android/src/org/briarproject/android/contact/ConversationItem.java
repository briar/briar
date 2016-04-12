package org.briarproject.android.contact;

import android.content.Context;

import org.briarproject.R;
import org.briarproject.api.introduction.IntroductionMessage;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.messaging.PrivateMessageHeader;
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

	public static ConversationItem from(PrivateMessageHeader h) {
		if (h.isLocal())
			return new ConversationMessageOutItem(h);
		else
			return new ConversationMessageInItem(h);
	}

	public static ConversationItem from(IntroductionRequest ir) {
		if (ir.isLocal()) {
			return new ConversationIntroductionOutItem(ir);
		} else {
			return new ConversationIntroductionInItem(ir);
		}
	}

	public static ConversationItem from(Context ctx, String contactName,
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
					ir.getTime(), ir.isSent(), ir.isSeen());
		} else {
			String text;
			if (ir.wasAccepted()) {
				text = ctx.getString(
						R.string.introduction_response_accepted_received,
						contactName, ir.getName());
			} else {
				text = ctx.getString(
						R.string.introduction_response_declined_received,
						contactName, ir.getName());
			}
			return new ConversationNoticeInItem(ir.getMessageId(), text,
					ir.getTime(), ir.isRead());
		}
	}

	/** This method should not be used to get user-facing objects,
	 *  Its purpose is to provider data for the contact list.
	 */
	public static ConversationItem from(IntroductionMessage im) {
		if (im.isLocal())
			return new ConversationNoticeOutItem(im.getMessageId(), "",
					im.getTime(), false, false);
		return new ConversationNoticeInItem(im.getMessageId(), "", im.getTime(),
				im.isRead());
	}

	protected interface OutgoingItem {
		MessageId getId();
		boolean isSent();
		void setSent(boolean sent);
		boolean isSeen();
		void setSeen(boolean seen);
	}

	protected interface IncomingItem {
		MessageId getId();
		boolean isRead();
		void setRead(boolean read);
	}

}
