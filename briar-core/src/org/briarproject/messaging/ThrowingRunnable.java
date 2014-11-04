package org.briarproject.messaging;

interface ThrowingRunnable<T extends Throwable> {

	public void run() throws T;
}
