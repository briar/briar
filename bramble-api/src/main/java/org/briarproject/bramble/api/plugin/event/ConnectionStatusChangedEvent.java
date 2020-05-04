package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionStatus;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a contact's connection status changes.
 */
@Immutable
@NotNullByDefault
public class ConnectionStatusChangedEvent extends Event {

	private final ContactId contactId;
	private final ConnectionStatus status;

	public ConnectionStatusChangedEvent(ContactId contactId,
			ConnectionStatus status) {
		this.contactId = contactId;
		this.status = status;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public ConnectionStatus getConnectionStatus() {
		return status;
	}
}
