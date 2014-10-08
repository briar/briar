package org.briarproject.api.transport;

import java.io.InputStream;

/** Decrypts and authenticates data received over an underlying transport. */
public interface StreamReader {

	/**
	 * Returns an input stream from which the decrypted, authenticated data can
	 * be read.
	 */
	InputStream getInputStream();
}
