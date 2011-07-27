package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet acknowledging receipt of one or more batches. */
public interface Ack {

	/**
	 * The maximum size of a serialised ack, excluding encryption and
	 * authentication.
	 */
	static final int MAX_SIZE = (1024 * 1024) - 100;

	/** Returns the IDs of the acknowledged batches. */
	Collection<BatchId> getBatchIds();
}
