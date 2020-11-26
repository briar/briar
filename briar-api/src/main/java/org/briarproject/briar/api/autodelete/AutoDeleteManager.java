package org.briarproject.briar.api.autodelete;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

@NotNullByDefault
public interface AutoDeleteManager {

	/**
	 * The unique ID of the auto-delete client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.autodelete");

	/**
	 * The current major version of the auto-delete client.
	 */
	int MAJOR_VERSION = 0;

	/**
	 * The current minor version of the auto-delete client.
	 */
	int MINOR_VERSION = 0;

	long getAutoDeleteTimer(Transaction txn, ContactId c) throws DbException;

	void setAutoDeleteTimer(Transaction txn, ContactId c, long timer)
			throws DbException;
}
