package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

interface BenchmarkTask<T> {

	void prepareBenchmark(Database<T> db) throws DbException;

	void runBenchmark(Database<T> db) throws DbException;
}
