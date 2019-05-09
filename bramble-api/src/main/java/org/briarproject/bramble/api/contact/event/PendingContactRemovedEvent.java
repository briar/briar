package org.briarproject.bramble.api.contact.event;

import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a pending contact is removed.
 */
@Immutable
@NotNullByDefault
public class PendingContactRemovedEvent extends Event {

	private final PendingContactId id;

	public PendingContactRemovedEvent(PendingContactId id) {
		this.id = id;
	}

	public PendingContactId getId() {
		return id;
	}

}
