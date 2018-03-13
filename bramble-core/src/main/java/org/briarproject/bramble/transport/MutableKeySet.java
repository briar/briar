package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.transport.KeySetId;

import javax.annotation.Nullable;

public class MutableKeySet {

	private final KeySetId keySetId;
	@Nullable
	private final ContactId contactId;
	private final MutableTransportKeys transportKeys;

	public MutableKeySet(KeySetId keySetId, @Nullable ContactId contactId,
			MutableTransportKeys transportKeys) {
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

	public MutableTransportKeys getTransportKeys() {
		return transportKeys;
	}
}
