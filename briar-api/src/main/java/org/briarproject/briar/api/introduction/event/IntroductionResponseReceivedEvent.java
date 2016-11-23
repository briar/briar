package org.briarproject.briar.api.introduction.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.introduction.IntroductionResponse;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class IntroductionResponseReceivedEvent extends Event {

	private final ContactId contactId;
	private final IntroductionResponse introductionResponse;

	public IntroductionResponseReceivedEvent(ContactId contactId,
			IntroductionResponse introductionResponse) {

		this.contactId = contactId;
		this.introductionResponse = introductionResponse;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public IntroductionResponse getIntroductionResponse() {
		return introductionResponse;
	}
}
