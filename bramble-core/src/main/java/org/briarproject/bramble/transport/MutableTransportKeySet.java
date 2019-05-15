package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.KeySetId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.nullsafety.NullSafety.requireExactlyOneNull;

@NotThreadSafe
@NotNullByDefault
class MutableTransportKeySet {

	private final KeySetId keySetId;
	@Nullable
	private final ContactId contactId;
	@Nullable
	private final PendingContactId pendingContactId;
	private final MutableTransportKeys keys;

	MutableTransportKeySet(KeySetId keySetId, @Nullable ContactId contactId,
			@Nullable PendingContactId pendingContactId,
			MutableTransportKeys keys) {
		requireExactlyOneNull(contactId, pendingContactId);
		this.keySetId = keySetId;
		this.contactId = contactId;
		this.pendingContactId = pendingContactId;
		this.keys = keys;
	}

	KeySetId getKeySetId() {
		return keySetId;
	}

	@Nullable
	ContactId getContactId() {
		return contactId;
	}

	@Nullable
	PendingContactId getPendingContactId() {
		return pendingContactId;
	}

	MutableTransportKeys getKeys() {
		return keys;
	}
}
