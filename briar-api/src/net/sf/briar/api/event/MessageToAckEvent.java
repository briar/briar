package net.sf.briar.api.event;

import net.sf.briar.api.ContactId;

/**
 * An event that is broadcast when a message is received from or offered by a
 * contact and needs to be acknowledged.
 */
public class MessageToAckEvent extends Event {

	private final ContactId contactId;

	public MessageToAckEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
