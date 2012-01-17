package net.sf.briar.transport;

import java.io.IOException;

interface IncomingEncryptionLayer {

	/**
	 * Reads a frame into the given buffer and returns its length, or -1 if no
	 * more frames can be read.
	 */
	int readFrame(byte[] b) throws IOException;
}
