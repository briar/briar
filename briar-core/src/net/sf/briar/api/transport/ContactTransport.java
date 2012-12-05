package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;

public class ContactTransport {

	private final ContactId contactId;
	private final TransportId transportId;
	private final long epoch, clockDiff, latency;
	private final boolean alice;

	public ContactTransport(ContactId contactId, TransportId transportId,
			long epoch, long clockDiff, long latency, boolean alice) {
		this.contactId = contactId;
		this.transportId = transportId;
		this.epoch = epoch;
		this.clockDiff = clockDiff;
		this.latency = latency;
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

	public long getClockDifference() {
		return clockDiff;
	}

	public long getLatency() {
		return latency;
	}

	public boolean getAlice() {
		return alice;
	}
}
