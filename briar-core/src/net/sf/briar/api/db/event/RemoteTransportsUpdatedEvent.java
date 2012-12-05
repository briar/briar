package net.sf.briar.api.db.event;

import java.util.Collection;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.Transport;

/** An event that is broadcast when a contact's transports are updated. */
public class RemoteTransportsUpdatedEvent extends DatabaseEvent {

	private final ContactId contactId;
	private final Collection<Transport> transports;

	public RemoteTransportsUpdatedEvent(ContactId contactId,
			Collection<Transport> transports) {
		this.contactId = contactId;
		this.transports = transports;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public Collection<Transport> getTransports() {
		return transports;
	}
}
