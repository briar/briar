package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;

/**
 * An interface for reading data from a transport connection. The reader is not
 * responsible for decrypting or authenticating the data.
 */
@NotNullByDefault
public interface TransportConnectionReader {

	/**
	 * Returns an input stream for reading from the transport connection.
	 */
	InputStream getInputStream() throws IOException;

	/**
	 * Marks this side of the transport connection closed. If the transport is
	 * simplex, the connection is closed. If the transport is duplex, the
	 * connection is closed if <tt>exception</tt> is true or the other side of
	 * the connection has been marked as closed.
	 *
	 * @param exception true if the connection is being closed because of an
	 * exception. This may affect how resources are disposed of.
	 * @param recognised true if the connection is definitely a Briar transport
	 * connection. This may affect how resources are disposed of.
	 */
	void dispose(boolean exception, boolean recognised) throws IOException;
}
