package org.briarproject.api.lifecycle;

import org.briarproject.api.clients.Client;

import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

/**
 * Manages the lifecycle of the app, starting {@link
 * org.briarproject.api.clients.Client Clients}, starting and stopping {@link
 * Service Services}, shutting down {@link java.util.concurrent.ExecutorService
 * ExecutorServices}, and opening and closing the {@link
 * org.briarproject.api.db.DatabaseComponent DatabaseComponent}.
 */
public interface LifecycleManager {

	/**
	 * The result of calling {@link LifecycleManager#startServices(String)}.
	 */
	enum StartResult {
		ALREADY_RUNNING, DB_ERROR, SERVICE_ERROR, SUCCESS
	}

	/**
	 * Registers a {@link Service} to be started and stopped.
	 */
	void registerService(Service s);

	/**
	 * Registers a {@link org.briarproject.api.clients.Client Client} to be
	 * started.
	 */
	void registerClient(Client c);

	/**
	 * Registers an {@link java.util.concurrent.ExecutorService ExecutorService}
	 * to be shut down.
	 */
	void registerForShutdown(ExecutorService e);

	/**
	 * Opens the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent}, creates a local author with the provided nick, and
	 * starts any registered {@link org.briarproject.api.clients.Client Clients}
	 * and {@link Service Services}.
	 */
	StartResult startServices(@Nullable String authorNick);

	/**
	 * Stops any registered {@link Service Services}, shuts down any
	 * registered {@link java.util.concurrent.ExecutorService ExecutorServices},
	 * and closes the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent}.
	 */
	void stopServices();

	/**
	 * Waits for the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent} to be opened before returning.
	 */
	void waitForDatabase() throws InterruptedException;

	/**
	 * Waits for the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent} to be opened and all registered {@link
	 * org.briarproject.api.clients.Client Clients} and {@link Service
	 * Services} to start before returning.
	 */
	void waitForStartup() throws InterruptedException;

	/**
	 * Waits for all registered {@link Service Services} to stop, all
	 * registered {@link java.util.concurrent.ExecutorService ExecutorServices}
	 * to shut down, and the {@link org.briarproject.api.db.DatabaseComponent
	 * DatabaseComponent} to be closed before returning.
	 */
	void waitForShutdown() throws InterruptedException;
}