package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * An interface for reading a bundle of acknowledgements, subscriptions,
 * transport details and batches.
 */
public interface BundleReader {

	/** Returns the size of the serialised bundle in bytes. */
	long getSize() throws IOException;

	/** Returns the bundle's header. */
	Header getHeader() throws IOException, GeneralSecurityException;

	/**
	 * Returns the next batch of messages, or null if there are no more batches.
	 */
	Batch getNextBatch() throws IOException, GeneralSecurityException;

	/** Finishes reading the bundle. */
	void close() throws IOException;
}
