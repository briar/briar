package org.briarproject.bramble.test;

import org.junit.After;
import org.junit.Before;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Logger.getLogger;

public abstract class BrambleTestCase {

	private static final Logger LOG =
			getLogger(BrambleTestCase.class.getName());

	@Nullable
	protected volatile Throwable exceptionInBackgroundThread = null;

	public BrambleTestCase() {
		// Ensure exceptions thrown on worker threads cause tests to fail
		UncaughtExceptionHandler fail = (thread, throwable) -> {
			LOG.log(Level.WARNING, "Caught unhandled exception", throwable);
			exceptionInBackgroundThread = throwable;
		};
		Thread.setDefaultUncaughtExceptionHandler(fail);
	}

	@Before
	public void beforeBrambleTestCase() {
		exceptionInBackgroundThread = null;
	}

	@After
	public void afterBrambleTestCase() {
		Throwable thrown = exceptionInBackgroundThread;
		LOG.warning("background thread has thrown an exception unexpectedly");
		if (thrown != null) throw new AssertionError(thrown);
	}
}
