package net.sf.briar.api.transport;

import java.io.InputStream;

/**
 * An interface for reading data from a batch-mode transport. The reader is not
 * responsible for decrypting or authenticating the data before returning it.
 */
public interface BatchTransportReader {

	/** Returns an input stream for reading from the transport. */
	InputStream getInputStream();

	/**
	 * Closes the reader and disposes of any associated state. The argument
	 * should be false if an exception was thrown while using the reader.
	 */
	void dispose(boolean success);
}
