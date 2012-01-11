package net.sf.briar.transport;

import java.io.IOException;

/** Encrypts authenticated data to be sent over a connection. */
interface ConnectionEncrypter {

	/** Encrypts and writes the given frame. */
	void writeFrame(byte[] b, int off, int len) throws IOException;

	/** Flushes the output stream. */
	void flush() throws IOException;

	/** Returns the maximum number of bytes that can be written. */
	long getRemainingCapacity();
}
