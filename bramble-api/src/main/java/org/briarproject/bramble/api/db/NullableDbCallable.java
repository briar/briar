package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface NullableDbCallable<R, E extends Exception> {

	@Nullable
	R call(Transaction txn) throws DbException, E;
}
