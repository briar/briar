package org.briarproject.bramble.test;

import org.junit.After;
import org.junit.Before;

import java.lang.Thread.UncaughtExceptionHandler;

import static org.junit.Assert.fail;

public abstract class BrambleTestCase {

	private volatile boolean exceptionInBackgroundThread = false;

	public BrambleTestCase() {
		// Ensure exceptions thrown on worker threads cause tests to fail
		UncaughtExceptionHandler fail = (thread, throwable) -> {
			throwable.printStackTrace();
			exceptionInBackgroundThread = true;
		};
		Thread.setDefaultUncaughtExceptionHandler(fail);
	}

	@Before
	public void before() {
		exceptionInBackgroundThread = false;
	}

	@After
	public void after() {
		if (exceptionInBackgroundThread) {
			fail("background thread has thrown an exception");
		}
	}
}
