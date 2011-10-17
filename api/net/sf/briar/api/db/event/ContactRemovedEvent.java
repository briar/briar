package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

/** An event that is broadcast when a contact is removed. */
public class ContactRemovedEvent extends ContactAddedEvent {

	public ContactRemovedEvent(ContactId contactId) {
		super(contactId);
	}
}
