package org.briarproject.api.transport;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.SecretKey;

public class StreamContext {

	private final ContactId contactId;
	private final TransportId transportId;
	private final SecretKey tagKey, headerKey;
	private final long streamNumber;

	public StreamContext(ContactId contactId, TransportId transportId,
			SecretKey tagKey, SecretKey headerKey, long streamNumber) {
		this.contactId = contactId;
		this.transportId = transportId;
		this.tagKey = tagKey;
		this.headerKey = headerKey;
		this.streamNumber = streamNumber;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public SecretKey getTagKey() {
		return tagKey;
	}

	public SecretKey getHeaderKey() {
		return headerKey;
	}

	public long getStreamNumber() {
		return streamNumber;
	}
}
