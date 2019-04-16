package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * A set of transport keys for communicating with a contact.
 */
@Immutable
@NotNullByDefault
public class TransportKeySet {

	private final TransportKeySetId keySetId;
	private final ContactId contactId;
	private final TransportKeys keys;

	public TransportKeySet(TransportKeySetId keySetId, ContactId contactId,
			TransportKeys keys) {
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.keys = keys;
	}

	public TransportKeySetId getKeySetId() {
		return keySetId;
	}

	public ContactId getContactId() {
		return contactId;
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
