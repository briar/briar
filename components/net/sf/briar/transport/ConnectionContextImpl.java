package net.sf.briar.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;

class ConnectionContextImpl implements ConnectionContext {

	private final ContactId contactId;
	private final TransportId transportId;
	private final TransportIndex transportIndex;
	private final long connectionNumber;

	ConnectionContextImpl(ContactId contactId, TransportId transportId,
			TransportIndex transportIndex, long connectionNumber) {
		this.contactId = contactId;
		this.transportId = transportId;
		this.transportIndex = transportIndex;
		this.connectionNumber = connectionNumber;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public TransportIndex getTransportIndex() {
		return transportIndex;
	}

	public long getConnectionNumber() {
		return connectionNumber;
	}
}
