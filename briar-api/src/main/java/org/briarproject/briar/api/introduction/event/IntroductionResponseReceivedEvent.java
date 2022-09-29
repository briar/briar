package org.briarproject.briar.api.introduction.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionResponseReceivedEvent extends
		ConversationMessageReceivedEvent<IntroductionResponse> {

	public IntroductionResponseReceivedEvent(
			IntroductionResponse introductionResponse, ContactId contactId) {
		super(introductionResponse, contactId);
	}

}
