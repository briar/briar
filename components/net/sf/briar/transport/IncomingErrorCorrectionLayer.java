package net.sf.briar.transport;

import java.io.IOException;
import java.util.Collection;

interface IncomingErrorCorrectionLayer {

	/**
	 * Reads a frame into the given buffer. The frame number must be contained
	 * in the given window. Returns false if no more frames can be read from
	 * the connection.
	 * @throws IOException if an unrecoverable error occurs and the connection
	 * must be closed.
	 * @throws InvalidDataException if a recoverable error occurs. The caller
	 * may choose whether to retry the read or close the connection.
	 */
	boolean readFrame(Frame f, Collection<Long> window) throws IOException,
	InvalidDataException;
}
