package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.messaging.Message;

/**
 * An event that is broadcast when a private message is added to the database.
 */
public class PrivateMessageAddedEvent extends DatabaseEvent {

	private final Message message;
	private final ContactId contactId;
	private final boolean incoming;

	public PrivateMessageAddedEvent(Message message, ContactId contactId,
			boolean incoming) {
		this.message = message;
		this.contactId = contactId;
		this.incoming = incoming;
	}

	public Message getMessage() {
		return message;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public boolean isIncoming() {
		return incoming;
	}
}
