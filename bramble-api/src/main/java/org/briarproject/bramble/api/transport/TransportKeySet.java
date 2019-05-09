package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.nullsafety.NullSafety.requireExactlyOneNull;

/**
 * A set of keys for communicating with a given contact or pending contact
 * over a given transport.
 */
@Immutable
@NotNullByDefault
public class TransportKeySet {

	private final KeySetId keySetId;
	@Nullable
	private final ContactId contactId;
	@Nullable
	private final PendingContactId pendingContactId;
	private final TransportKeys keys;

	public TransportKeySet(KeySetId keySetId, @Nullable ContactId contactId,
			@Nullable PendingContactId pendingContactId, TransportKeys keys) {
		requireExactlyOneNull(contactId, pendingContactId);
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.pendingContactId = pendingContactId;
		this.keys = keys;
	}

	public KeySetId getKeySetId() {
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

	public TransportKeys getKeys() {
		return keys;
	}

	@Override
	public int hashCode() {
		return keySetId.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TransportKeySet &&
				keySetId.equals(((TransportKeySet) o).keySetId);
	}
}
