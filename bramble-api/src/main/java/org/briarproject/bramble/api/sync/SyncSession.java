package org.briarproject.bramble.api.sync;

import java.io.IOException;

public interface SyncSession {

	/**
	 * Runs the session. This method returns when there are no more records to
	 * send or receive, or when the {@link #interrupt()} method has been called.
	 */
	void run() throws IOException;

	/**
	 * Interrupts the session, causing the {@link #run()} method to return at
	 * the next opportunity.
	 */
	void interrupt();
}
