package org.briarproject.android.contact;

import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.sync.MessageId;

public class ConversationIntroductionInItem extends ConversationItem implements
		ConversationItem.IncomingItem {

	private IntroductionRequest ir;
	private boolean answered, read;

	public ConversationIntroductionInItem(IntroductionRequest ir) {
		super(ir.getMessageId(), ir.getTime());

		this.ir = ir;
		this.answered = ir.wasAnswered();
		this.read = ir.isRead();
	}

	@Override
	int getType() {
		return INTRODUCTION_IN;
	}

	public IntroductionRequest getIntroductionRequest() {
		return ir;
	}

	public boolean wasAnswered() {
		return answered;
	}

	public void setAnswered(boolean answered) {
		this.answered = answered;
	}

	@Override
	public boolean isRead() {
		return read;
	}

	@Override
	public void setRead(boolean read) {
		this.read = read;
	}

}
