package org.briarproject.api.event;

import org.briarproject.api.ContactId;

/**
 * An event that is broadcast when the retention time of a contact's database
 * changes.
 */
public class RemoteRetentionTimeUpdatedEvent extends Event {

	private final ContactId contactId;

	public RemoteRetentionTimeUpdatedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
