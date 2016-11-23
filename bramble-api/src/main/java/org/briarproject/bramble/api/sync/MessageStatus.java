package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.contact.ContactId;

public class MessageStatus {

	private final MessageId messageId;
	private final ContactId contactId;
	private final boolean sent, seen;

	public MessageStatus(MessageId messageId, ContactId contactId,
			boolean sent, boolean seen) {
		this.messageId = messageId;
		this.contactId = contactId;
		this.sent = sent;
		this.seen = seen;
	}

	/**
	 * Returns the ID of the message.
	 */
	public MessageId getMessageId() {
		return messageId;
	}

	/**
	 * Returns the ID of the contact.
	 */
	public ContactId getContactId() {
		return contactId;
	}

	/**
	 * Returns true if the message has been sent to the contact.
	 */
	public boolean isSent() {
		return sent;
	}

	/**
	 * Returns true if the message has been seen by the contact.
	 */
	public boolean isSeen() {
		return seen;
	}
}
