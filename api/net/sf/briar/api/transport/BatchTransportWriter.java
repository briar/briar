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
	 * Returns true if the output stream should be flushed after each packet.
	 */
	boolean shouldFlush();

	/**
	 * Closes the writer and disposes of any associated resources. The
	 * argument indicates whether the writer is being closed because of an
	 * exception, which may affect how resources are disposed of.
	 */
	void dispose(boolean exception);
}
