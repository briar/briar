package net.sf.briar.transport;

import java.io.IOException;

interface IncomingReliabilityLayer {

	/**
	 * Reads and returns a frame, possibly using the given buffer. Returns null
	 * if no more frames can be read from the connection.
	 * @throws IOException if an unrecoverable error occurs and the connection
	 * must be closed.
	 * @throws InvalidDataException if a recoverable error occurs. The caller
	 * may choose whether to retry the read or close the connection.
	 */
	Frame readFrame(Frame f) throws IOException, InvalidDataException;

	/** Returns the maximum length in bytes of the frames this layer returns. */
	int getMaxFrameLength();
}
