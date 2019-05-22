package org.briarproject.bramble.api.contact.event;

import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a pending contact is added.
 */
@Immutable
@NotNullByDefault
public class PendingContactAddedEvent extends Event {

	private final PendingContact pendingContact;

	public PendingContactAddedEvent(PendingContact pendingContact) {
		this.pendingContact = pendingContact;
	}

	public PendingContact getPendingContact() {
		return pendingContact;
	}
}
