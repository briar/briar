package org.briarproject.bramble.test;

import java.lang.Thread.UncaughtExceptionHandler;

import static org.junit.Assert.fail;

public abstract class BrambleTestCase {

	public BrambleTestCase() {
		// Ensure exceptions thrown on worker threads cause tests to fail
		UncaughtExceptionHandler fail = (thread, throwable) -> {
			throwable.printStackTrace();
			fail();
		};
		Thread.setDefaultUncaughtExceptionHandler(fail);
	}
}
