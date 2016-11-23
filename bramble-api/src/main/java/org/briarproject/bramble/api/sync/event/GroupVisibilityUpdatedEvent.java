package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when the visibility of a group is updated.
 */
@Immutable
@NotNullByDefault
public class GroupVisibilityUpdatedEvent extends Event {

	private final Collection<ContactId> affected;

	public GroupVisibilityUpdatedEvent(Collection<ContactId> affected) {
		this.affected = affected;
	}

	/**
	 * Returns the contacts affected by the update.
	 */
	public Collection<ContactId> getAffectedContacts() {
		return affected;
	}
}
