package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.SignatureException;

/**
 * A bundle of acknowledgements, subscriptions, transport details and batches.
 */
public interface Bundle {

	/** Returns the size of the serialised bundle in bytes. */
	long getSize() throws IOException;

	/** Returns the bundle's header. */
	Header getHeader() throws IOException, SignatureException;

	/**
	 * Returns the next batch of messages, or null if there are no more batches.
	 */
	Batch getNextBatch() throws IOException, SignatureException;
}
