package org.briarproject.bramble.api.db;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface DbCallable<R, E extends Exception> {

	R call(Transaction txn) throws DbException, E;
}
