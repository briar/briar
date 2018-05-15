package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.transport.KeySetId;

class MutableKeySet {

	private final KeySetId keySetId;
	private final ContactId contactId;
	private final MutableTransportKeys transportKeys;

	MutableKeySet(KeySetId keySetId, ContactId contactId,
			MutableTransportKeys transportKeys) {
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.transportKeys = transportKeys;
	}

	KeySetId getKeySetId() {
		return keySetId;
	}

	ContactId getContactId() {
		return contactId;
	}

	MutableTransportKeys getTransportKeys() {
		return transportKeys;
	}
}
