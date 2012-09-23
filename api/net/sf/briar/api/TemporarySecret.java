package net.sf.briar.api;

import net.sf.briar.api.protocol.TransportId;

public class TemporarySecret {

	protected final ContactId contactId;
	protected final TransportId transportId;
	protected final long period, outgoing, centre;
	protected final byte[] secret, bitmap;

	public TemporarySecret(ContactId contactId, TransportId transportId,
			long period, byte[] secret, long outgoing, long centre,
			byte[] bitmap) {
		this.contactId = contactId;
		this.transportId = transportId;
		this.period = period;
		this.secret = secret;
		this.outgoing = outgoing;
		this.centre = centre;
		this.bitmap = bitmap;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public TransportId getTransportId() {
		return transportId;
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
