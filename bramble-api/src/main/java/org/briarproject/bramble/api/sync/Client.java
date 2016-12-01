package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface Client {

	/**
	 * Called at startup to create any local state needed by the client.
	 */
	void createLocalState(Transaction txn) throws DbException;
}
