package org.briarproject.android.contact;

import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.sync.MessageId;

public class ConversationIntroductionInItem extends ConversationIntroductionItem
		implements ConversationItem.IncomingItem {

	private boolean read;

	public ConversationIntroductionInItem(IntroductionRequest ir) {
		super(ir);

		this.read = ir.isRead();
	}

	@Override
	int getType() {
		return INTRODUCTION_IN;
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
