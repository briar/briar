package org.briarproject.api.transport;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;

public class StreamContext {

	private final ContactId contactId;
	private final TransportId transportId;
	private final byte[] secret;
	private final long streamNumber;
	private final boolean alice;

	public StreamContext(ContactId contactId, TransportId transportId,
			byte[] secret, long streamNumber, boolean alice) {
		this.contactId = contactId;
		this.transportId = transportId;
		this.secret = secret;
		this.streamNumber = streamNumber;
		this.alice = alice;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public byte[] getSecret() {
		return secret;
	}

	public long getStreamNumber() {
		return streamNumber;
	}

	public boolean getAlice() {
		return alice;
	}
}
