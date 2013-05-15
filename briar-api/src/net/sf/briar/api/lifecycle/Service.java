package net.sf.briar.api.lifecycle;

public interface Service {

	/** Starts the service and returns true if it started successfully. */
	public boolean start();

	/** Stops the service and returns true if it stopped successfully. */
	public boolean stop();
}
