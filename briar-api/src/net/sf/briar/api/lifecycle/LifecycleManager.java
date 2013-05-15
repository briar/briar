package net.sf.briar.api.lifecycle;

import java.util.concurrent.ExecutorService;

public interface LifecycleManager {

	/** Registers a {@link Service} to be started and stopped. */
	public void register(Service s);

	/**
	 * Registers an {@link java.util.concurrent.ExecutorService ExecutorService}
	 * to be shut down.
	 */
	public void registerForShutdown(ExecutorService e);

	/**  Starts any registered {@link Service}s. */
	public void startServices();

	/**
	 * Stops any registered {@link Service}s and shuts down any registered
	 * {@link java.util.concurrent.ExecutorService ExecutorService}s.
	 */
	public void stopServices();

	/** Waits for the database to be opened before returning. */
	public void waitForDatabase() throws InterruptedException;

	/** Waits for all registered {@link Service}s to start before returning. */
	public void waitForStartup() throws InterruptedException;

	/**
	 * Waits for all registered {@link Service}s to stop and all registered
	 * {@link java.util.concurrent.ExecutorService ExecutorService}s to shut
	 * down before returning.
	 */
	public void waitForShutdown() throws InterruptedException;
}