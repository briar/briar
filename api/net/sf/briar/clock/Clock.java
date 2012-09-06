package net.sf.briar.clock;

/**
 * An interface for time-related system functions that allows them to be
 * replaced for testing.
 */
public interface Clock {

	/** @see {@link java.lang.System#currentTimeMillis()} */
	long currentTimeMillis();
}
