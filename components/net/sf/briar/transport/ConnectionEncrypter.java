package net.sf.briar.transport;

import java.io.IOException;
import java.io.OutputStream;

/** Encrypts authenticated data to be sent over a connection. */
interface ConnectionEncrypter {

	/** Returns an output stream to which unencrypted data can be written. */
	OutputStream getOutputStream();

	/** Encrypts and writes the MAC for the current frame. */
	void writeMac(byte[] mac) throws IOException;

	/**
	 * Returns the number of encrypted bytes that can be written without
	 * writing more than the given number of bytes, including encryption
	 * overhead.
	 */
	long getCapacity(long capacity);
}
