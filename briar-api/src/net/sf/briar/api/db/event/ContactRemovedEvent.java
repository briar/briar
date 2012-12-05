package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

/** An event that is broadcast when a contact is removed. */
public class ContactRemovedEvent extends DatabaseEvent {

	private final ContactId contactId;

	public ContactRemovedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
