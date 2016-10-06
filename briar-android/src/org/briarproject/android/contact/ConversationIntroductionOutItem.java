package org.briarproject.android.contact;

import org.briarproject.api.introduction.IntroductionRequest;

/**
 * This class is needed and can not be replaced by an ConversationNoticeOutItem,
 * because it carries the optional introduction message
 * to be displayed as a regular private message.
 *
 *  This class is not thread-safe
 */
class ConversationIntroductionOutItem extends ConversationIntroductionItem
		implements ConversationItem.OutgoingItem {

	private boolean sent, seen;

	ConversationIntroductionOutItem(IntroductionRequest ir) {
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
