package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;

/** An event that is broadcast when a message is added to the database. */
public class MessageAddedEvent extends Event {

	private final Message message;
	private final ContactId contactId;

	public MessageAddedEvent(Message message, ContactId contactId) {
		this.message = message;
		this.contactId = contactId;
	}

	/** Returns the message that was added. */
	public Message getMessage() {
		return message;
	}

	/** Returns the ID of the group to which the message belongs. */
	public GroupId getGroupId() {
		return message.getGroupId();
	}

	/**
	 * Returns the ID of the contact from which the message was received, or
	 * null if the message was locally generated.
	 */
	public ContactId getContactId() {
		return contactId;
	}
}
