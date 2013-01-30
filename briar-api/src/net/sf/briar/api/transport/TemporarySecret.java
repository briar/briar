package net.sf.briar.api.transport;

import static net.sf.briar.api.transport.TransportConstants.CONNECTION_WINDOW_SIZE;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.messaging.TransportId;

public class TemporarySecret extends Endpoint {

	private final long period, outgoing, centre;
	private final byte[] secret, bitmap;

	/** Creates a temporary secret with the given connection window. */
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

	/** Creates a temporary secret with a new connection window. */
	public TemporarySecret(ContactId contactId, TransportId transportId,
			long epoch, long clockDiff, long latency, boolean alice,
			long period, byte[] secret) {
		this(contactId, transportId, epoch, clockDiff, latency, alice, period,
				secret, 0L, 0L, new byte[CONNECTION_WINDOW_SIZE / 8]);
	}

	/** Creates a temporary secret derived from the given endpoint. */
	public TemporarySecret(Endpoint ep, long period, byte[] secret) {
		this(ep.getContactId(), ep.getTransportId(), ep.getEpoch(),
				ep.getClockDifference(), ep.getLatency(), ep.getAlice(),
				period, secret);
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
