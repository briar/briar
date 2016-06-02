package org.briarproject.api.conversation;

import org.briarproject.api.introduction.IntroductionRequest;

public interface ConversationIntroductionRequestItem extends ConversationItem {

	IntroductionRequest getIntroductionRequest();

	boolean wasAnswered();

	void setAnswered(boolean answered);
}
