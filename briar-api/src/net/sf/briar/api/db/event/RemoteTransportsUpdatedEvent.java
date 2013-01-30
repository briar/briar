package net.sf.briar.api.db.event;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.messaging.TransportId;

/**
 * An event that is broadcast when a contact's remote transport properties
 * are updated.
 */
public class RemoteTransportsUpdatedEvent extends DatabaseEvent {

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
