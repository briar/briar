package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a message is received from, or offered by, a
 * contact and needs to be acknowledged.
 */
@Immutable
@NotNullByDefault
public class MessageToAckEvent extends Event {

	private final ContactId contactId;

	public MessageToAckEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
