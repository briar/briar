package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface DbRunnable<E extends Exception> {

	void run(Transaction txn) throws DbException, E;
}
