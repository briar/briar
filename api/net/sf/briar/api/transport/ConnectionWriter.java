package net.sf.briar.api.transport;

import java.io.OutputStream;

/** Encrypts and authenticates data to be sent over a connection. */
public interface ConnectionWriter {

	/**
	 * Returns an output stream to which unencrypted, unauthenticated data can
	 * be written.
	 */
	OutputStream getOutputStream();
}
