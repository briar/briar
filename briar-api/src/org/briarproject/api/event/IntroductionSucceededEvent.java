package org.briarproject.api.event;

import org.briarproject.api.contact.Contact;

public class IntroductionSucceededEvent extends Event {

	private final Contact contact;

	public IntroductionSucceededEvent(Contact contact) {
		this.contact = contact;
	}

	public Contact getContact() {
		return contact;
	}
}
