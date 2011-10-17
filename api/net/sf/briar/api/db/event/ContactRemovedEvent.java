package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

public class ContactRemovedEvent extends ContactAddedEvent {

	public ContactRemovedEvent(ContactId contactId) {
		super(contactId);
	}
}
