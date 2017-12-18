package org.briarproject.bramble.db;

interface BenchmarkTask<T> {

	void run(T context) throws Exception;
}
