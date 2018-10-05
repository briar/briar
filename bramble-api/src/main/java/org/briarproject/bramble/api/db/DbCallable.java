package org.briarproject.bramble.api.db;

public interface DbCallable<R, E extends Exception> {

	R call(Transaction txn) throws DbException, E;
}
