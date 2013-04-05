package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;

public class Endpoint {

	protected final ContactId contactId;
	protected final TransportId transportId;
	private final long epoch;
	private final boolean alice;

	public Endpoint(ContactId contactId, TransportId transportId, long epoch,
			boolean alice) {
		this.contactId = contactId;
		this.transportId = transportId;
		this.epoch = epoch;
		this.alice = alice;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public long getEpoch() {
		return epoch;
	}

	public boolean getAlice() {
		return alice;
	}
}
