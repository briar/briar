package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ConnectionClosedEvent extends Event {

	private final ContactId contactId;
	private final TransportId transportId;
	private final boolean incoming;

	public ConnectionClosedEvent(ContactId contactId, TransportId transportId,
			boolean incoming) {
		this.contactId = contactId;
		this.transportId = transportId;
		this.incoming = incoming;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public boolean isIncoming() {
		return incoming;
	}
}
