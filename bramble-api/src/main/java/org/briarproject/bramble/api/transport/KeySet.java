package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A set of transport keys for communicating with a contact. If the keys have
 * not yet been bound to a contact, {@link #getContactId()}} returns null.
 */
@Immutable
@NotNullByDefault
public class KeySet {

	private final KeySetId keySetId;
	@Nullable
	private final ContactId contactId;
	private final TransportKeys transportKeys;

	public KeySet(KeySetId keySetId, @Nullable ContactId contactId,
			TransportKeys transportKeys) {
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.transportKeys = transportKeys;
	}

	public KeySetId getKeySetId() {
		return keySetId;
	}

	@Nullable
	public ContactId getContactId() {
		return contactId;
	}

	public TransportKeys getTransportKeys() {
		return transportKeys;
	}

	@Override
	public int hashCode() {
		return keySetId.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof KeySet && keySetId.equals(((KeySet) o).keySetId);
	}
}
