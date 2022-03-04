package org.briarproject.bramble.test;

import org.junit.After;
import org.junit.Before;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Logger.getLogger;
import static org.junit.Assert.fail;

public abstract class BrambleTestCase {

	private static final Logger LOG =
			getLogger(BrambleTestCase.class.getName());

	protected volatile boolean exceptionInBackgroundThread = false;

	public BrambleTestCase() {
		// Ensure exceptions thrown on worker threads cause tests to fail
		UncaughtExceptionHandler fail = (thread, throwable) -> {
			LOG.log(Level.WARNING, "Caught unhandled exception", throwable);
			exceptionInBackgroundThread = true;
		};
		Thread.setDefaultUncaughtExceptionHandler(fail);
	}

	@Before
	public void beforeBrambleTestCase() {
		exceptionInBackgroundThread = false;
	}

	@After
	public void afterBrambleTestCase() {
		if (exceptionInBackgroundThread) {
			fail("background thread has thrown an exception unexpectedly");
		}
	}
}
