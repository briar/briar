package org.briarproject.api.event;

import java.util.Collection;

import org.briarproject.api.ContactId;

/**
 * An event that is broadcast when the set of subscriptions visible to one or
 * more contacts is updated.
 */
public class LocalSubscriptionsUpdatedEvent extends Event {

	private final Collection<ContactId> affected;

	public LocalSubscriptionsUpdatedEvent(Collection<ContactId> affected) {
		this.affected = affected;
	}

	/** Returns the contacts affected by the update. */
	public Collection<ContactId> getAffectedContacts() {
		return affected;
	}
}
