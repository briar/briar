package org.briarproject.api.messaging;

import java.io.IOException;

public interface MessagingSession {

	/**
	 * Runs the session. This method returns when there are no more packets to
	 * send or when the {@link #interrupt()} method has been called.
	 */
	void run() throws IOException;

	/**
	 * Interrupts the session, causing the {@link #run()} method to return at
	 * the next opportunity or throw an {@link java.io.IOException IOException}
	 * if it cannot return cleanly.
	 */
	void interrupt();
}
