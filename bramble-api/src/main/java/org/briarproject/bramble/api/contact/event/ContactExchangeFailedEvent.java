package org.briarproject.bramble.api.contact.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public class ContactExchangeFailedEvent extends Event {

	@Nullable
	private final Author duplicateRemoteAuthor;

	public ContactExchangeFailedEvent(@Nullable Author duplicateRemoteAuthor) {
		this.duplicateRemoteAuthor = duplicateRemoteAuthor;
	}

	public ContactExchangeFailedEvent() {
		this(null);
	}

	@Nullable
	public Author getDuplicateRemoteAuthor() {
		return duplicateRemoteAuthor;
	}

	public boolean wasDuplicateContact() {
		return duplicateRemoteAuthor != null;
	}

}
