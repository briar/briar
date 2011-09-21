package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet acknowledging receipt of one or more batches. */
public interface Ack {

	/** Returns the IDs of the acknowledged batches. */
	Collection<BatchId> getBatchIds();
}
