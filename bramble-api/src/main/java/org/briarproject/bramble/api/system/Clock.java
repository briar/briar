package org.briarproject.bramble.api.system;

/**
 * An interface for time-related system functions that allows them to be
 * replaced for testing.
 */
public interface Clock {

	/**
	 * The minimum reasonable value for the system clock, in milliseconds
	 * since the Unix epoch.
	 * <p/>
	 * 1 Jan 2021, 00:00:00 UTC
	 */
	long MIN_REASONABLE_TIME_MS = 1_609_459_200_000L;

	/**
	 * The maximum reasonable value for the system clock, in milliseconds
	 * since the Unix epoch.
	 * <p/>
	 * 1 Jan 2121, 00:00:00 UTC
	 */
	long MAX_REASONABLE_TIME_MS = 4_765_132_800_000L;

	/**
	 * @see System#currentTimeMillis()
	 */
	long currentTimeMillis();

	/**
	 * @see Thread#sleep(long)
	 */
	void sleep(long milliseconds) throws InterruptedException;
}
