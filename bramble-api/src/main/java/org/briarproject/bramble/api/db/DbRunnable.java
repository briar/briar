package org.briarproject.bramble.api.db;

public interface DbRunnable<E extends Exception> {

	void run(Transaction txn) throws DbException, E;
}
