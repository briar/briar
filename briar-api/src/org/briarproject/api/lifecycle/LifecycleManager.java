package org.briarproject.api.lifecycle;

import java.util.concurrent.ExecutorService;

/**
 * Manages the lifecycle of the app, starting and stopping {@link Service
 * Services}, shutting down {@link java.util.concurrent.ExecutorService
 * ExecutorServices}, and opening and closing the {@link
 * org.briarproject.api.db.DatabaseComponent DatabaseComponent}.
 */
public interface LifecycleManager {

	/** The result of calling {@link LifecycleManager#startServices()}. */
	enum StartResult { ALREADY_RUNNING, DB_ERROR, SERVICE_ERROR, SUCCESS }

	/** Registers a {@link Service} to be started and stopped. */
	public void register(Service s);

	/**
	 * Registers an {@link java.util.concurrent.ExecutorService ExecutorService}
	 * to be shut down.
	 */
	public void registerForShutdown(ExecutorService e);

	/**
	 * Starts any registered {@link Service Services} and opens the {@link
	 * org.briarproject.api.db.DatabaseComponent DatabaseComponent}.
	 */
	public StartResult startServices();

	/**
	 * Stops any registered {@link Service Services}, shuts down any
	 * registered {@link java.util.concurrent.ExecutorService ExecutorServices},
	 * and closes the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent}.
	 */
	public void stopServices();

	/**
	 * Waits for the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent} to be opened before returning.
	 */
	public void waitForDatabase() throws InterruptedException;

	/**
	 * Waits for the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent} to be opened and all registered {@link Service
	 * Services} to start before returning.
	 */
	public void waitForStartup() throws InterruptedException;

	/**
	 * Waits for all registered {@link Service Services} to stop, all
	 * registered {@link java.util.concurrent.ExecutorService ExecutorServices}
	 * to shut down, and the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent} to be closed before returning.
	 */
	public void waitForShutdown() throws InterruptedException;
}