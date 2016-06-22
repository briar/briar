package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.MessageId;

public abstract class InvitationReceivedEvent extends Event {

	private final ContactId contactId;
	private final MessageId messageId;

	public InvitationReceivedEvent(ContactId contactId, MessageId messageId) {
		this.contactId = contactId;
		this.messageId = messageId;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public MessageId getMessageId() {
		return messageId;
	}
}
