package org.briarproject.bramble.test;

import java.lang.Thread.UncaughtExceptionHandler;

import static org.junit.Assert.fail;

public abstract class BrambleTestCase {

	public BrambleTestCase() {
		// Ensure exceptions thrown on worker threads cause tests to fail
		UncaughtExceptionHandler fail = new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable throwable) {
				throwable.printStackTrace();
				fail();
			}
		};
		Thread.setDefaultUncaughtExceptionHandler(fail);
	}
}
