package net.sf.briar.api.db.event;

import java.util.Collection;

import net.sf.briar.api.ContactId;

public class SubscriptionsUpdatedEvent extends DatabaseEvent {

	private final Collection<ContactId> affectedContacts;

	// FIXME: Replace this constructor
	public SubscriptionsUpdatedEvent() {
		affectedContacts = null;
	}

	public Collection<ContactId> getAffectedContacts() {
		return affectedContacts;
	}
}
