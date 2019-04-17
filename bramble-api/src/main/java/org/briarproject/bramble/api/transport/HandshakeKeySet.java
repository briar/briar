package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A set of keys for handshaking with a given contact or pending contact over a
 * given transport. Unlike a {@link TransportKeySet} these keys do not provide
 * forward secrecy.
 */
@Immutable
@NotNullByDefault
public class HandshakeKeySet {

	private final HandshakeKeySetId keySetId;
	@Nullable
	private final ContactId contactId;
	@Nullable
	private final PendingContactId pendingContactId;
	private final HandshakeKeys keys;

	public HandshakeKeySet(HandshakeKeySetId keySetId, ContactId contactId,
			HandshakeKeys keys) {
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.keys = keys;
		pendingContactId = null;
	}

	public HandshakeKeySet(HandshakeKeySetId keySetId,
			PendingContactId pendingContactId, HandshakeKeys keys) {
		this.keySetId = keySetId;
		this.pendingContactId = pendingContactId;
		this.keys = keys;
		contactId = null;
	}

	public HandshakeKeySetId getKeySetId() {
		return keySetId;
	}

	@Nullable
	public ContactId getContactId() {
		return contactId;
	}

	@Nullable
	public PendingContactId getPendingContactId() {
		return pendingContactId;
	}

	public HandshakeKeys getKeys() {
		return keys;
	}

	@Override
	public int hashCode() {
		return keySetId.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof HandshakeKeySet &&
				keySetId.equals(((HandshakeKeySet) o).keySetId);
	}
}
