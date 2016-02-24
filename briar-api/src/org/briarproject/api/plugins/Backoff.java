package org.briarproject.api.plugins;

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
