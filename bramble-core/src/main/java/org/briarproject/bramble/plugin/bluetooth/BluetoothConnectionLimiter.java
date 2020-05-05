package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

@NotNullByDefault
interface BluetoothConnectionLimiter {

	/**
	 * Informs the limiter that key agreement has started.
	 */
	void keyAgreementStarted();

	/**
	 * Informs the limiter that key agreement has ended.
	 */
	void keyAgreementEnded();

	/**
	 * Returns true if a contact connection can be opened. This method does not
	 * need to be called for key agreement connections.
	 */
	boolean canOpenContactConnection();

	/**
	 * Informs the limiter that the given connection has been opened.
	 */
	void connectionOpened(DuplexTransportConnection conn);

	/**
	 * Informs the limiter that the given connection has been closed.
	 */
	void connectionClosed(DuplexTransportConnection conn);

	/**
	 * Informs the limiter that all connections have been closed.
	 */
	void allConnectionsClosed();
}
