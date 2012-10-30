package net.sf.briar.transport;

import java.io.IOException;

interface FrameWriter {

	/** Writes the given frame. */
	void writeFrame(byte[] frame, int payloadLength, boolean finalFrame)
			throws IOException;

	/** Flushes the stack. */
	void flush() throws IOException;

	/** Returns the maximum number of bytes that can be written. */
	long getRemainingCapacity();
}
