package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

/** An event that is broadcast when a message is received. */
public class MessageReceivedEvent extends DatabaseEvent {

	private final ContactId contactId;

	public MessageReceivedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
