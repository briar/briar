package net.sf.briar.transport;

import java.io.IOException;

interface OutgoingErrorCorrectionLayer {

	/** Writes the given frame. */
	void writeFrame(Frame f) throws IOException;

	/** Flushes the stack. */
	void flush() throws IOException;

	/** Returns the maximum number of bytes that can be written. */
	long getRemainingCapacity();
}
