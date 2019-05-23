package org.briarproject.bramble.api.rendezvous.event;

import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a rendezvous with a pending contact fails.
 */
@Immutable
@NotNullByDefault
public class RendezvousFailedEvent extends Event {

	private final PendingContactId pendingContactId;

	public RendezvousFailedEvent(PendingContactId pendingContactId) {
		this.pendingContactId = pendingContactId;
	}

	public PendingContactId getPendingContactId() {
		return pendingContactId;
	}
}
