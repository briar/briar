package net.sf.briar.transport;

import java.io.IOException;
import java.io.InputStream;

interface PacketDecrypter {

	/** Returns the input stream from which packets should be read. */
	InputStream getInputStream();

	/**
	 * Reads, decrypts and returns a tag from the underlying input stream.
	 * Returns null if the end of the input stream is reached before any bytes
	 * are read.
	 */
	byte[] readTag() throws IOException;
}
