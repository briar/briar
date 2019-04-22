package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.transport.TransportKeySetId;

class MutableKeySet {

	private final TransportKeySetId keySetId;
	private final ContactId contactId;
	private final MutableTransportKeys transportKeys;

	MutableKeySet(TransportKeySetId keySetId, ContactId contactId,
			MutableTransportKeys transportKeys) {
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.transportKeys = transportKeys;
	}

	TransportKeySetId getKeySetId() {
		return keySetId;
	}

	ContactId getContactId() {
		return contactId;
	}

	MutableTransportKeys getTransportKeys() {
		return transportKeys;
	}
}
