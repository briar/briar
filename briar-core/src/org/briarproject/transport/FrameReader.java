package org.briarproject.transport;

import java.io.IOException;

interface FrameReader {

	/**
	 * Reads a frame into the given buffer and returns its payload length, or
	 * -1 if no more frames can be read from the connection.
	 */
	int readFrame(byte[] frame) throws IOException;
}
