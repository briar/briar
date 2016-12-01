package org.briarproject.bramble.api.lifecycle;

public interface Service {

	/**
	 * Starts the service.This method must not be called concurrently with
	 * {@link #stopService()}.
	 */
	void startService() throws ServiceException;

	/**
	 * Stops the service. This method must not be called concurrently with
	 * {@link #startService()}.
	 */
	void stopService() throws ServiceException;
}
