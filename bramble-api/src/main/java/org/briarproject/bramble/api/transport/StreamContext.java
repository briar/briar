package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.nullsafety.NullSafety.requireExactlyOneNull;

@Immutable
@NotNullByDefault
public class StreamContext {

	@Nullable
	private final ContactId contactId;
	@Nullable
	private final PendingContactId pendingContactId;
	private final TransportId transportId;
	private final SecretKey tagKey, headerKey;
	private final long streamNumber;
	private final boolean handshakeMode;

	public StreamContext(@Nullable ContactId contactId,
			@Nullable PendingContactId pendingContactId,
			TransportId transportId, SecretKey tagKey, SecretKey headerKey,
			long streamNumber, boolean handshakeMode) {
		requireExactlyOneNull(contactId, pendingContactId);
		this.contactId = contactId;
		this.pendingContactId = pendingContactId;
		this.transportId = transportId;
		this.tagKey = tagKey;
		this.headerKey = headerKey;
		this.streamNumber = streamNumber;
		this.handshakeMode = handshakeMode;
	}

	@Nullable
	public ContactId getContactId() {
		return contactId;
	}

	@Nullable
	public PendingContactId getPendingContactId() {
		return pendingContactId;
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

	public boolean isHandshakeMode() {
		return handshakeMode;
	}
}
