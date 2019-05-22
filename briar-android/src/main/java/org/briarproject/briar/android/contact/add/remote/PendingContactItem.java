package org.briarproject.briar.android.contact.add.remote;

import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactState;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class PendingContactItem {

	private final PendingContact pendingContact;
	private final PendingContactState state;

	PendingContactItem(PendingContact pendingContact,
			PendingContactState state) {
		this.pendingContact = pendingContact;
		this.state = state;
	}

	PendingContact getPendingContact() {
		return pendingContact;
	}

	PendingContactState getState() {
		return state;
	}
}
