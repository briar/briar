package org.briarproject.bramble.api;

public interface ThrowingRunnable<T extends Throwable> {

	void run() throws T;
}
