package org.briarproject.api.transport;

import java.io.OutputStream;

/** Encrypts and authenticates data to be sent over an underlying transport. */
public interface StreamWriter {

	/**
	 * Returns an output stream to which unencrypted, unauthenticated data can
	 * be written.
	 */
	OutputStream getOutputStream();
}
