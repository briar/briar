package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.introduction.IntroductionRequest;

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
