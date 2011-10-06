package net.sf.briar.api.transport.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An interface for reading and writing data over a stream-mode transport. The
 * connection is not responsible for encrypting/decrypting or authenticating
 * the data.
 */
public interface StreamTransportConnection {

	/** Returns an input stream for reading from the connection. */
	InputStream getInputStream() throws IOException;

	/** Returns an output stream for writing to the connection. */
	OutputStream getOutputStream() throws IOException;

	/**
	 * Closes the connection and disposes of any associated state. The argument
	 * should be false if an exception was thrown while using the connection.
	 */
	void dispose(boolean success) throws IOException;
}
