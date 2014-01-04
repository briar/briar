package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

/**
 * An event that is broadcast when a message is received or offered from a
 * contact and needs to be acknowledged.
 */
public class MessageToAckEvent extends DatabaseEvent {

	private final ContactId contactId;

	public MessageToAckEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
