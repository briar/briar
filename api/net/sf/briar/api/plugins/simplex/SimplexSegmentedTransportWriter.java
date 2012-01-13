package net.sf.briar.api.plugins.simplex;

import net.sf.briar.api.plugins.FrameSink;

/**
 * An interface for writing data to a simplex segmented transport. The writer is
 * not responsible for authenticating or encrypting the data before writing it.
 */
public interface SimplexSegmentedTransportWriter extends FrameSink {

	/** Returns the capacity of the transport in bytes. */
	long getCapacity();

	/** Returns the maximum length of a segment in bytes. */
	int getMaximumSegmentLength();

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
