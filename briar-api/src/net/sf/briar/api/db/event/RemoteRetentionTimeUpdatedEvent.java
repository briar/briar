package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

/**
 * An event that is broadcast when the retention time of a contact's database
 * changes.
 */
public class RemoteRetentionTimeUpdatedEvent extends DatabaseEvent {

	private final ContactId contactId;

	public RemoteRetentionTimeUpdatedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
