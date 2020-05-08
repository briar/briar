package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@NotNullByDefault
interface BluetoothConnectionLimiter {

	/**
	 * How long a connection must remain open before it's considered stable.
	 */
	long STABILITY_PERIOD_MS = SECONDS.toMillis(90);

	/**
	 * The minimum interval between attempts to raise the connection limit.
	 * This is longer than {@link #STABILITY_PERIOD_MS} so we don't start
	 * another attempt before knowing the outcome of the last one.
	 */
	long MIN_ATTEMPT_INTERVAL_MS = MINUTES.toMillis(2);

	/**
	 * The maximum interval between attempts to raise the connection limit.
	 */
	long MAX_ATTEMPT_INTERVAL_MS = DAYS.toMillis(2);

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
	 * Informs the limiter that a contact connection has been opened.
	 * <p/>
	 * Returns true if the connection is allowed.
	 */
	boolean contactConnectionOpened(DuplexTransportConnection conn,
			boolean incoming);

	/**
	 * Informs the limiter that a key agreement connection has been opened.
	 */
	void keyAgreementConnectionOpened(DuplexTransportConnection conn);

	/**
	 * Informs the limiter that the given connection has been closed.
	 *
	 * @param exception True if the connection was closed due to an exception.
	 */
	void connectionClosed(DuplexTransportConnection conn, boolean exception);

	/**
	 * Informs the limiter that the Bluetooth adapter has been disabled.
	 */
	void bluetoothDisabled();
}
