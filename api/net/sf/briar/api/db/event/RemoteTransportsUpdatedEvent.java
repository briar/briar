package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

/** An event that is broadcast when a contact's transports are updated. */
public class RemoteTransportsUpdatedEvent extends DatabaseEvent {

	private final ContactId contactId;

	public RemoteTransportsUpdatedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
