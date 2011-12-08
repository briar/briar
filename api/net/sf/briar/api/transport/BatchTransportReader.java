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
	 * Closes the reader and disposes of any associated resources. The first
	 * argument indicates whether the reader is being closed because of an
	 * exception and the second argument indicates whether the connection was
	 * recognised, which may affect how resources are disposed of.
	 */
	void dispose(boolean exception, boolean recognised);
}
