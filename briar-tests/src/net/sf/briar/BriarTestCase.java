package net.sf.briar;

import java.lang.Thread.UncaughtExceptionHandler;

import junit.framework.TestCase;

public abstract class BriarTestCase extends TestCase {

	public BriarTestCase() {
		// Ensure exceptions thrown on worker threads cause tests to fail
		UncaughtExceptionHandler fail = new UncaughtExceptionHandler() {
			public void uncaughtException(Thread thread, Throwable throwable) {
				fail();
			}
		};
		Thread.setDefaultUncaughtExceptionHandler(fail);
	}
}
