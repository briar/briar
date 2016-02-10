package org.briarproject.api.event;

import org.briarproject.api.contact.ContactId;

import java.util.Collection;

/** An event that is broadcast when the visibility of a group is updated. */
public class GroupVisibilityUpdatedEvent extends Event {

	private final Collection<ContactId> affected;

	public GroupVisibilityUpdatedEvent(Collection<ContactId> affected) {
		this.affected = affected;
	}

	/** Returns the contacts affected by the update. */
	public Collection<ContactId> getAffectedContacts() {
		return affected;
	}
}
