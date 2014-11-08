package org.briarproject.api.lifecycle;

public interface Service {

	/**
	 * Starts the service and returns true if it started successfully.
	 * This method must not be called concurrently with {@link #stop()}.
	 */
	public boolean start();

	/**
	 * Stops the service and returns true if it stopped successfully.
	 * This method must not be called concurrently with {@link #start()}.
	 */
	public boolean stop();
}
