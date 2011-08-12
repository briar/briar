package net.sf.briar.transport;

import java.io.IOException;
import java.io.InputStream;

interface PacketDecrypter {

	/** Returns the input stream from which packets should be read. */
	InputStream getInputStream();

	/** Reads, decrypts and returns a tag from the underlying input stream. */
	byte[] readTag() throws IOException;
}
