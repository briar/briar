package org.briarproject.briar.api.introduction.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionRequestReceivedEvent extends
		PrivateMessageReceivedEvent<IntroductionRequest> {

	public IntroductionRequestReceivedEvent(
			IntroductionRequest introductionRequest, ContactId contactId) {
		super(introductionRequest, contactId);
	}

}
