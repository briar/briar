package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * A set of transport keys for communicating with a contact.
 */
@Immutable
@NotNullByDefault
public class KeySet {

	private final KeySetId keySetId;
	private final ContactId contactId;
	private final TransportKeys transportKeys;

	public KeySet(KeySetId keySetId, ContactId contactId,
			TransportKeys transportKeys) {
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.transportKeys = transportKeys;
	}

	public KeySetId getKeySetId() {
		return keySetId;
	}

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
