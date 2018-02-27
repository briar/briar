package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

interface ConnectionChooser {

	/**
	 * Submits a connection task to the chooser.
	 */
	void submit(Callable<KeyAgreementConnection> task);

	/**
	 * Returns a connection returned by any of the tasks submitted to the
	 * chooser, waiting up to the given amount of time for a connection if
	 * necessary. Returns null if the time elapses without a connection
	 * becoming available.
	 *
	 * @param timeout the timeout in milliseconds
	 * @throws InterruptedException if the thread is interrupted while waiting
	 * for a connection to become available
	 */
	@Nullable
	KeyAgreementConnection poll(long timeout) throws InterruptedException;

	/**
	 * Stops the chooser. Any connections already returned to the chooser are
	 * closed unless they have been removed from the chooser by calling
	 * {@link #poll(long)}. Any connections subsequently returned to the
	 * chooser will also be closed.
	 */
	void stop();
}
