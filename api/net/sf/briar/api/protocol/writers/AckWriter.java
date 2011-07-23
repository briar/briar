package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.BatchId;

/** An interface for creating an ack. */
public interface AckWriter {

	/**
	 * Attempts to add the given BatchId to the ack and returns true if it
	 * was added.
	 */
	boolean addBatchId(BatchId b) throws IOException;

	/** Finishes writing the ack. */
	void finish() throws IOException;
}
