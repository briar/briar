package net.sf.briar.api.transport.batch;

import java.io.IOException;
import java.io.InputStream;

/**
 * An interface for reading data from a batch-mode transport. The reader is not
 * responsible for decrypting or authenticating the data before returning it.
 */
public interface BatchTransportReader {

	/** Returns an input stream for reading from the transport. */
	InputStream getInputStream() throws IOException;

	/**
	 * Closes the reader and disposes of any associated state. This method must
	 * be called even if the reader is not used, or if an exception is thrown
	 * while using the reader.
	 */
	void dispose() throws IOException;
}
