package net.sf.briar.api.transport;

import java.io.OutputStream;

/**
 * An interface for writing data to a batch-mode transport. The writer is not
 * responsible for authenticating or encrypting the data before writing it.
 */
public interface BatchTransportWriter {

	/** Returns the capacity of the transport in bytes. */
	long getCapacity();

	/** Returns an output stream for writing to the transport. */
	OutputStream getOutputStream();

	/**
	 * Closes the writer and disposes of any associated state. The argument
	 * should be false if an exception was thrown while using the writer.
	 */
	void dispose(boolean success);
}
