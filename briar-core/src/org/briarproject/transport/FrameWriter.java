package org.briarproject.transport;

import java.io.IOException;

interface FrameWriter {

	/** Writes the given frame. */
	void writeFrame(byte[] frame, int payloadLength, boolean finalFrame)
			throws IOException;

	/** Flushes the stream. */
	void flush() throws IOException;
}
