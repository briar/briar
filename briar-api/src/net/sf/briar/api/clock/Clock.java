package net.sf.briar.api.clock;

/**
 * An interface for time-related system functions that allows them to be
 * replaced for testing.
 */
public interface Clock {

	/** @see {@link java.lang.System#currentTimeMillis()} */
	long currentTimeMillis();

	/** @see {@link java.lang.Thread.sleep(long)} */
	void sleep(long milliseconds) throws InterruptedException;
}
