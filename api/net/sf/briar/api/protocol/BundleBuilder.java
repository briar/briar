package net.sf.briar.api.protocol;

import java.io.IOException;

public interface BundleBuilder {

	/** Returns the bundle's capacity in bytes. */
	long getCapacity() throws IOException;

	/** Adds a header to the bundle. */
	void addHeader(Header h) throws IOException;

	/** Adds a batch of messages to the bundle. */
	void addBatch(Batch b) throws IOException;

	/** Builds and returns the bundle. */
	Bundle build() throws IOException;
}
