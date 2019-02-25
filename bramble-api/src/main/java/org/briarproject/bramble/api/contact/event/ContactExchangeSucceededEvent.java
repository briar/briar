package org.briarproject.bramble.api.contact.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class ContactExchangeSucceededEvent extends Event {

	private final Author remoteAuthor;

	public ContactExchangeSucceededEvent(Author remoteAuthor) {
		this.remoteAuthor = remoteAuthor;
	}

	public Author getRemoteAuthor() {
		return remoteAuthor;
	}

}
