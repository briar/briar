package net.sf.briar.transport;

import java.io.IOException;

interface FrameReader {

	/**
	 * Reads a frame into the given buffer. Returns false if no more frames can
	 * be read from the connection.
	 */
	boolean readFrame(Frame f) throws IOException;
}
