package net.sf.briar.transport;

import java.io.IOException;

import net.sf.briar.api.transport.Segment;

interface OutgoingEncryptionLayer {

	/** Writes the given segment. */
	void writeSegment(Segment s) throws IOException;

	/** Flushes the stack. */
	void flush() throws IOException;

	/** Returns the maximum number of bytes that can be written. */
	long getRemainingCapacity();

	/**
	 * Returns the maximum length in bytes of the segments this layer accepts.
	 */
	int getMaxSegmentLength();
}
