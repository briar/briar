package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when the retransmission time of one or more
 * messages has changed.
 */
@Immutable
@NotNullByDefault
public class RetransmissionTimeChangedEvent extends Event {

	private final ContactId contactId;

	public RetransmissionTimeChangedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
