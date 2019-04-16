package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A set of transport keys for communicating with a contact or pending contact.
 * Unlike a {@link TransportKeySet} these keys do not provide forward secrecy.
 */
@Immutable
@NotNullByDefault
public class StaticTransportKeySet {

	private final StaticTransportKeySetId keySetId;
	@Nullable
	private final ContactId contactId;
	@Nullable
	private final PendingContactId pendingContactId;
	private final StaticTransportKeys keys;

	public StaticTransportKeySet(StaticTransportKeySetId keySetId,
			ContactId contactId, StaticTransportKeys keys) {
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.keys = keys;
		pendingContactId = null;
	}

	public StaticTransportKeySet(StaticTransportKeySetId keySetId,
			PendingContactId pendingContactId, StaticTransportKeys keys) {
		this.keySetId = keySetId;
		this.pendingContactId = pendingContactId;
		this.keys = keys;
		contactId = null;
	}

	public StaticTransportKeySetId getKeySetId() {
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

	public StaticTransportKeys getKeys() {
		return keys;
	}

	@Override
	public int hashCode() {
		return keySetId.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof StaticTransportKeySet &&
				keySetId.equals(((StaticTransportKeySet) o).keySetId);
	}
}
