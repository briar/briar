package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

@NotNullByDefault
interface BluetoothConnectionManager {

	/**
	 * Returns true if a contact connection can be opened without exceeding
	 * the connection limit. This method does not need to be called for key
	 * exchange connections.
	 */
	boolean canOpenConnection();

	/**
	 * Passes a newly opened connection to the manager. The manager may close
	 * the new connection or another connection to stay within the connection
	 * limit.
	 * <p/>
	 * Returns false if the manager has closed the new connection (this will
	 * never be the case for key exchange connections).
	 */
	boolean connectionOpened(DuplexTransportConnection conn,
			boolean isForKeyExchange);

	/**
	 * Informs the manager that the given connection has been closed.
	 */
	void connectionClosed(DuplexTransportConnection conn);

	/**
	 * Informs the manager that all connections have been closed.
	 */
	void allConnectionsClosed();
}
