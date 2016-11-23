package org.briarproject.bramble.api.lifecycle;

import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;

import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

/**
 * Manages the lifecycle of the app, starting {@link Client Clients}, starting
 * and stopping {@link Service Services}, shutting down
 * {@link ExecutorService ExecutorServices}, and opening and closing the
 * {@link DatabaseComponent}.
 */
@NotNullByDefault
public interface LifecycleManager {

	/**
	 * The result of calling {@link #startServices(String)}.
	 */
	enum StartResult {
		ALREADY_RUNNING, DB_ERROR, SERVICE_ERROR, SUCCESS
	}

	/**
	 * Registers a {@link Service} to be started and stopped.
	 */
	void registerService(Service s);

	/**
	 * Registers a {@link Client} to be started.
	 */
	void registerClient(Client c);

	/**
	 * Registers an {@link ExecutorService} to be shut down.
	 */
	void registerForShutdown(ExecutorService e);

	/**
	 * Opens the {@link DatabaseComponent}, optionally creates a local author
	 * with the provided nickname, and starts any registered
	 * {@link Client Clients} and {@link Service Services}.
	 */
	StartResult startServices(@Nullable String nickname);

	/**
	 * Stops any registered {@link Service Services}, shuts down any
	 * registered {@link ExecutorService ExecutorServices}, and closes the
	 * {@link DatabaseComponent}.
	 */
	void stopServices();

	/**
	 * Waits for the {@link DatabaseComponent} to be opened before returning.
	 */
	void waitForDatabase() throws InterruptedException;

	/**
	 * Waits for the {@link DatabaseComponent} to be opened and all registered
	 * {@link Client Clients} and {@link Service Services} to start before
	 * returning.
	 */
	void waitForStartup() throws InterruptedException;

	/**
	 * Waits for all registered {@link Service Services} to stop, all
	 * registered {@link ExecutorService ExecutorServices} to shut down, and
	 * the {@link DatabaseComponent} to be closed before returning.
	 */
	void waitForShutdown() throws InterruptedException;
}