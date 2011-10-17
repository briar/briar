package net.sf.briar.api.db.event;

import java.util.Collection;
import java.util.Collections;

import net.sf.briar.api.ContactId;

/**
 * An event that is broadcast when the set of subscriptions visible to one or
 * more contacts is updated.
 */
public class SubscriptionsUpdatedEvent extends DatabaseEvent {

	private final Collection<ContactId> affectedContacts;

	public SubscriptionsUpdatedEvent() {
		affectedContacts = Collections.emptyList();
	}

	public SubscriptionsUpdatedEvent(Collection<ContactId> affectedContacts) {
		this.affectedContacts = affectedContacts;
	}

	/** Returns the contacts affected by the update. */
	public Collection<ContactId> getAffectedContacts() {
		return affectedContacts;
	}
}
