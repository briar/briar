package org.briarproject.bramble.api.rendezvous.event;

import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a rendezvous connection is closed.
 */
@Immutable
@NotNullByDefault
public class RendezvousConnectionClosedEvent extends Event {

	private final PendingContactId pendingContactId;
	private final boolean success;

	public RendezvousConnectionClosedEvent(PendingContactId pendingContactId,
			boolean success) {
		this.pendingContactId = pendingContactId;
		this.success = success;
	}

	public PendingContactId getPendingContactId() {
		return pendingContactId;
	}

	public boolean isSuccess() {
		return success;
	}
}
