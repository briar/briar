package org.briarproject.bramble.sync;

interface ThrowingRunnable<T extends Throwable> {

	void run() throws T;
}
