package net.sf.briar.api.transport;

import java.io.OutputStream;

/** Encrypts and authenticates data to be sent over a connection. */
public interface ConnectionWriter {

	/**
	 * Returns an output stream to which unencrypted, unauthenticated data can
	 * be written.
	 */
	OutputStream getOutputStream();

	/**
	 * Returns the number of encrypted and authenticated bytes that can be
	 * written without writing more than the given number of bytes, including
	 * encryption and authentication overhead.
	 */
	long getCapacity(long capacity);
}
