package net.sf.briar.transport;

import java.io.IOException;
import java.io.InputStream;

/** Decrypts unauthenticated data received over a connection. */
interface ConnectionDecrypter {

	/** Returns an input stream from which decrypted data can be read. */
	InputStream getInputStream();

	/** Reads and decrypts the MAC for the current frame. */
	void readMac(byte[] mac) throws IOException;
}
