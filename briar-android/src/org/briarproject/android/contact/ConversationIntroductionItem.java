package org.briarproject.android.contact;

import org.briarproject.api.introduction.IntroductionRequest;
import org.jetbrains.annotations.NotNull;

// This class is not thread-safe
abstract class ConversationIntroductionItem extends ConversationItem {

	private final IntroductionRequest ir;
	private boolean answered;

	ConversationIntroductionItem(@NotNull IntroductionRequest ir) {
		super(ir.getMessageId(), ir.getGroupId(), ir.getTimestamp());

		this.ir = ir;
		this.answered = ir.wasAnswered();
	}

	@NotNull
	IntroductionRequest getIntroductionRequest() {
		return ir;
	}

	boolean wasAnswered() {
		return answered;
	}

	void setAnswered(boolean answered) {
		this.answered = answered;
	}
}
