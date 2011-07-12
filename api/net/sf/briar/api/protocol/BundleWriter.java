package net.sf.briar.api.protocol;

import java.io.IOException;

/**
 * An interface for writing a bundle of acknowledgements, subscriptions,
 * transport details and batches.
 */
public interface BundleWriter {

	/** Returns the bundle's capacity in bytes. */
	long getCapacity() throws IOException;

	/** Adds a header to the bundle. */
	void addHeader(Header h) throws IOException;

	/** Adds a batch of messages to the bundle. */
	void addBatch(Batch b) throws IOException;

	/** Finishes writing the bundle. */
	void close() throws IOException;
}
