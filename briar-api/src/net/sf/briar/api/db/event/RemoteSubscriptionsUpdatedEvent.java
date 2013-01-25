package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;

/**  An event that is broadcast when a contact's subscriptions are updated. */
public class RemoteSubscriptionsUpdatedEvent extends DatabaseEvent {

	private final ContactId contactId;

	public RemoteSubscriptionsUpdatedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
