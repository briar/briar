package org.briarproject.bramble.plugin.bluetooth;

interface BluetoothConnectionManager {

	/**
	 * Returns true if a contact connection can be opened without exceeding
	 * the connection limit.
	 */
	boolean canOpenConnection();

	/**
	 * Increments the number of open connections and returns true if the new
	 * connection can be kept open without exceeding the connection limit.
	 */
	boolean connectionOpened();

	/**
	 * Decrements the number of open connections.
	 */
	void connectionClosed();

	/**
	 * Resets the number of open connections.
	 */
	void allConnectionsClosed();
}
