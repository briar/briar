package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

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
