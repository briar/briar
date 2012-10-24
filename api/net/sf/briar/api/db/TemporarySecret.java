package net.sf.briar.api.db;

import static net.sf.briar.api.transport.TransportConstants.CONNECTION_WINDOW_SIZE;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;

public class TemporarySecret extends ContactTransport {

	private final long period, outgoing, centre;
	private final byte[] secret, bitmap;

	public TemporarySecret(ContactId contactId, TransportId transportId,
			long epoch, long clockDiff, long latency, boolean alice,
			long period, byte[] secret, long outgoing, long centre,
			byte[] bitmap) {
		super(contactId, transportId, epoch, clockDiff, latency, alice);
		this.period = period;
		this.secret = secret;
		this.outgoing = outgoing;
		this.centre = centre;
		this.bitmap = bitmap;
	}

	public TemporarySecret(TemporarySecret old, long period, byte[] secret) {
		super(old.getContactId(), old.getTransportId(), old.getEpoch(),
				old.getClockDifference(), old.getLatency(), old.getAlice());
		this.period = period;
		this.secret = secret;
		outgoing = 0L;
		centre = 0L;
		bitmap = new byte[CONNECTION_WINDOW_SIZE / 8];
	}

	public long getPeriod() {
		return period;
	}

	public byte[] getSecret() {
		return secret;
	}

	public long getOutgoingConnectionCounter() {
		return outgoing;
	}

	public long getWindowCentre() {
		return centre;
	}

	public byte[] getWindowBitmap() {
		return bitmap;
	}
}
