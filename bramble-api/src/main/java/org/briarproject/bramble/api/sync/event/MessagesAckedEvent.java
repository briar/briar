package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when messages are acked by a contact.
 */
@Immutable
@NotNullByDefault
public class MessagesAckedEvent extends Event {

	private final ContactId contactId;
	private final Collection<MessageId> acked;

	public MessagesAckedEvent(ContactId contactId,
			Collection<MessageId> acked) {
		this.contactId = contactId;
		this.acked = acked;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public Collection<MessageId> getMessageIds() {
		return acked;
	}
}
