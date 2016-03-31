package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.introduction.IntroductionResponse;

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
