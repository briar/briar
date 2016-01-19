package org.briarproject.android.contact;

import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.sync.MessageId;

/**
 * This class is needed and can not be replaced by an ConversationNoticeOutItem,
 * because it carries the optional introduction message
 * to be displayed as a regular private message.
 */
public class ConversationIntroductionOutItem
		extends ConversationIntroductionInItem
		implements ConversationItem.OutgoingItem {

	private boolean sent, seen;

	public ConversationIntroductionOutItem(IntroductionRequest ir) {
		super(ir);
		this.sent = ir.isSent();
		this.seen = ir.isSeen();
	}

	@Override
	int getType() {
		return INTRODUCTION_OUT;
	}

	@Override
	public boolean isSent() {
		return sent;
	}

	@Override
	public void setSent(boolean sent) {
		this.sent = sent;
	}

	@Override
	public boolean isSeen() {
		return seen;
	}

	@Override
	public void setSeen(boolean seen) {
		this.seen = seen;
	}

}
