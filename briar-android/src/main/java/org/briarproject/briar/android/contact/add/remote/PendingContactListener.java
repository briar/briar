package org.briarproject.briar.android.contact.add.remote;

import org.briarproject.bramble.api.contact.PendingContact;

interface PendingContactListener {

	void onFailedPendingContactRemoved(PendingContact pendingContact);

}
