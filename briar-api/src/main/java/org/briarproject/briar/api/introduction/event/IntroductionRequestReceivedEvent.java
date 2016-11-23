package org.briarproject.briar.api.introduction.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.introduction.IntroductionRequest;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionRequestReceivedEvent extends Event {

	private final ContactId contactId;
	private final IntroductionRequest introductionRequest;

	public IntroductionRequestReceivedEvent(ContactId contactId,
			IntroductionRequest introductionRequest) {

		this.contactId = contactId;
		this.introductionRequest = introductionRequest;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public IntroductionRequest getIntroductionRequest() {
		return introductionRequest;
	}

}
