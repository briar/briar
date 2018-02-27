package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;

import javax.annotation.Nullable;

interface ConnectionChooser {

	/**
	 * Adds a connection to the set of connections that may be chosen. If
	 * {@link #stop()} has already been called, the connection will be closed
	 * immediately.
	 */
	void addConnection(KeyAgreementConnection c);

	/**
	 * Chooses one of the connections passed to
	 * {@link #addConnection(KeyAgreementConnection)} and returns it,
	 * waiting up to the given amount of time for a suitable connection to
	 * become available. Returns null if the time elapses without a suitable
	 * connection becoming available.
	 *
	 * @param alice true if the local party is Alice
	 * @param timeout the timeout in milliseconds
	 * @throws InterruptedException if the thread is interrupted while waiting
	 * for a suitable connection to become available
	 */
	@Nullable
	KeyAgreementConnection chooseConnection(boolean alice, long timeout)
			throws InterruptedException;

	/**
	 * Stops the chooser from accepting new connections. Closes any connections
	 * already passed to {@link #addConnection(KeyAgreementConnection)}
	 * and not chosen. Any connections subsequently passed to
	 * {@link #addConnection(KeyAgreementConnection)} will be closed.
	 */
	void stop();
}
