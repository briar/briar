package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An interface for writing data to a transport connection. The writer is not
 * responsible for authenticating or encrypting the data.
 */
@NotNullByDefault
public interface TransportConnectionWriter {

	/**
	 * Returns the maximum latency of the transport in milliseconds.
	 */
	int getMaxLatency();

	/**
	 * Returns the maximum idle time of the transport in milliseconds.
	 */
	int getMaxIdleTime();

	/**
	 * Returns the capacity of the transport connection in bytes.
	 */
	long getCapacity();

	/**
	 * Returns an output stream for writing to the transport connection.
	 */
	OutputStream getOutputStream() throws IOException;

	/**
	 * Marks this side of the transport connection closed. If the transport is
	 * simplex, the connection is closed. If the transport is duplex, the
	 * connection is closed if <tt>exception</tt> is true or the other side of
	 * the connection has been marked as closed.
	 *
	 * @param exception true if the connection is being closed because of an
	 * exception. This may affect how resources are disposed of.
	 */
	void dispose(boolean exception) throws IOException;
}
