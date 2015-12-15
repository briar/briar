package org.briarproject.api.event;

import org.briarproject.api.ContactId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

/** An event that is broadcast when messages are acked by a contact. */
public class MessagesAckedEvent extends Event {

	private final ContactId contactId;
	private final Collection<MessageId> acked;

	public MessagesAckedEvent(ContactId contactId,
			Collection<MessageId> acked ) {
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
