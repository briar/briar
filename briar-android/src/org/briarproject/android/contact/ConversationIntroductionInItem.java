package org.briarproject.android.contact;

import org.briarproject.android.contact.ConversationItem.IncomingItem;
import org.briarproject.api.introduction.IntroductionRequest;
import org.jetbrains.annotations.NotNull;

// This class is not thread-safe
class ConversationIntroductionInItem extends ConversationIntroductionItem
		implements IncomingItem {

	private boolean read;

	ConversationIntroductionInItem(@NotNull IntroductionRequest ir) {
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
