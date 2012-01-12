package net.sf.briar.transport;

import java.io.IOException;

/** Decrypts unauthenticated data received over a connection. */
interface ConnectionDecrypter {

	/**
	 * Reads and decrypts a frame into the given buffer and returns the length
	 * of the decrypted frame, or -1 if no more frames can be read.
	 */
	int readFrame(byte[] b) throws IOException;
}
