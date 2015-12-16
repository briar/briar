package org.briarproject.sync;

interface ThrowingRunnable<T extends Throwable> {

	void run() throws T;
}
