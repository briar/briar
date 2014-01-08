package org.briarproject.api.event;

import org.briarproject.api.ContactId;

/**  An event that is broadcast when a contact's subscriptions are updated. */
public class RemoteSubscriptionsUpdatedEvent extends Event {

	private final ContactId contactId;

	public RemoteSubscriptionsUpdatedEvent(ContactId contactId) {
		this.contactId = contactId;
	}

	public ContactId getContactId() {
		return contactId;
	}
}
