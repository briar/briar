package org.briarproject.bramble.api.system;

/**
 * An interface for time-related system functions that allows them to be
 * replaced for testing.
 */
public interface Clock {

	/**
	 * @see {@link System#currentTimeMillis()}
	 */
	long currentTimeMillis();

	/**
	 * @see {@link Thread#sleep(long)}
	 */
	void sleep(long milliseconds) throws InterruptedException;
}
