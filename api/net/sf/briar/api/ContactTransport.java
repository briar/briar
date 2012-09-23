package net.sf.briar.api;

import net.sf.briar.api.protocol.TransportId;

public class ContactTransport extends TemporarySecret {

	private final long epoch, clockDiff, latency;
	private final boolean alice;

	public ContactTransport(ContactId contactId, TransportId transportId,
			long epoch, long clockDiff, long latency, boolean alice,
			long period, byte[] secret, long outgoing, long centre,
			byte[] bitmap) {
		super(contactId, transportId, period, secret, outgoing, centre, bitmap);
		this.epoch = epoch;
		this.clockDiff = clockDiff;
		this.latency = latency;
		this.alice = alice;
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
