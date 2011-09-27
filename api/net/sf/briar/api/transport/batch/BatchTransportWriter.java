package net.sf.briar.api.transport.batch;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An interface for writing data to a batch-mode transport. The writer is not
 * responsible for authenticating or encrypting the data before writing it.
 */
public interface BatchTransportWriter {

	/** Returns the maximum number of bytes that can be written. */
	long getCapacity();

	/** Returns an output stream for writing to the transport. */
	OutputStream getOutputStream();

	/**
	 * Closes the writer and disposes of any associated state. This method must
	 * be called even if the writer is not used, or if an exception is thrown
	 * while using the writer.
	 */
	void dispose() throws IOException;
}
