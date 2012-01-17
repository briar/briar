package net.sf.briar.transport;

import java.io.IOException;
import java.util.Collection;

interface IncomingErrorCorrectionLayer {

	/**
	 * Reads a frame into the given buffer. The frame number must be contained
	 * in the given window. Returns false if no more frames can be read from
	 * the connection.
	 */
	boolean readFrame(Frame f, Collection<Long> window) throws IOException;
}
