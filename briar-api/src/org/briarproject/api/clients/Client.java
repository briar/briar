package org.briarproject.api.clients;

import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;

public interface Client {

	/**
	 * Called at startup to create any local state needed by the client.
	 */
	void createLocalState(Transaction txn) throws DbException;
}
