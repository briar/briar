package org.briarproject.bramble.api.plugin;

/**
 * Calculates polling intervals for transport plugins that use backoff.
 */
public interface Backoff {

	/**
	 * Returns the current polling interval.
	 */
	int getPollingInterval();

	/**
	 * Increments the backoff counter.
	 */
	void increment();

	/**
	 * Resets the backoff counter.
	 */
	void reset();
}
