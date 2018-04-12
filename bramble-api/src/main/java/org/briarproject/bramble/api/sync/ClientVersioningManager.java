package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group.Visibility;

@NotNullByDefault
public interface ClientVersioningManager {

	/**
	 * The unique ID of the versioning client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.bramble.versioning");

	/**
	 * The current version of the versioning client.
	 */
	int CLIENT_VERSION = 0;

	void registerClient(ClientId clientId, int clientVersion);

	void registerClientVersioningHook(ClientId clientId, int clientVersion,
			ClientVersioningHook hook);

	interface ClientVersioningHook {

		void onClientVisibilityChanging(Transaction txn, Contact c,
				Visibility v) throws DbException;
	}
}
