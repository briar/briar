package org.briarproject.api.event;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;

/**
 * An event that is broadcast when a contact's remote transport properties
 * are updated.
 */
public class RemoteTransportsUpdatedEvent extends Event {

	private final ContactId contactId;
	private final TransportId transportId;

	public RemoteTransportsUpdatedEvent(ContactId contactId,
			TransportId transportId) {
		this.contactId = contactId;
		this.transportId = transportId;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public TransportId getTransportId() {
		return transportId;
	}
}
