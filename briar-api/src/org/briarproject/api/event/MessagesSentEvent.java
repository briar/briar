package org.briarproject.api.event;

import java.util.Collection;

import org.briarproject.api.ContactId;
import org.briarproject.api.messaging.MessageId;

/** An event that is broadcast when messages are sent to a contact. */
public class MessagesSentEvent extends Event {

	private final ContactId contactId;
	private final Collection<MessageId> messageIds;

	public MessagesSentEvent(ContactId contactId,
	                         Collection<MessageId> messageIds) {
		this.contactId = contactId;
		this.messageIds = messageIds;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public Collection<MessageId> getMessageIds() {
		return messageIds;
	}
}
