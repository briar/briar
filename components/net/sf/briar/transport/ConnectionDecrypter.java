package net.sf.briar.transport;

import java.io.IOException;
import java.io.InputStream;

/** Decrypts unauthenticated data received over a connection. */
interface ConnectionDecrypter {

	/** Returns an input stream from which decrypted data can be read. */
	InputStream getInputStream();

	/** Reads and decrypts the remainder of the current frame. */
	void readFinal(byte[] b) throws IOException;
}
