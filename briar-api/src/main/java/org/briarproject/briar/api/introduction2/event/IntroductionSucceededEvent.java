package org.briarproject.briar.api.introduction2.event;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
// TODO still needed?
public class IntroductionSucceededEvent extends Event {

	private final Contact contact;

	public IntroductionSucceededEvent(Contact contact) {
		this.contact = contact;
	}

	public Contact getContact() {
		return contact;
	}
}
