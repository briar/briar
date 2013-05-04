package net.sf.briar.api.lifecycle;

public interface LifecycleManager {

	/** Starts any services that need to be started at startup. */
	public void startServices();

	/** Stops any services that need to be stopped at shutdown. */
	public void stopServices();

	/** Waits for the database to be opened before returning. */
	public void waitForDatabase() throws InterruptedException;

	/** Waits for all services to start before returning. */
	public void waitForStartup() throws InterruptedException;

	/** Waits for all services to stop before returning. */
	public void waitForShutdown() throws InterruptedException;
}
