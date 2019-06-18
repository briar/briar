package org.briarproject.briar.api.messaging.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a new attachment is received.
 */
@Immutable
@NotNullByDefault
public class AttachmentReceivedEvent extends Event {

	private final MessageId messageId;
	private final ContactId contactId;

	public AttachmentReceivedEvent(MessageId messageId, ContactId contactId) {
		this.messageId = messageId;
		this.contactId = contactId;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
