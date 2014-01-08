package org.briarproject.api.transport;

import java.io.InputStream;

/** Decrypts and authenticates data received over a connection. */
public interface ConnectionReader {

	/**
	 * Returns an input stream from which the decrypted, authenticated data can
	 * be read.
	 */
	InputStream getInputStream();
}
