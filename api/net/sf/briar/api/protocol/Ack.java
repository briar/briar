package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet acknowledging receipt of one or more batches. */
public interface Ack {

	/** The maximum number of batch IDs per ack. */
	static final int MAX_IDS_PER_ACK = 29959;

	/** Returns the IDs of the acknowledged batches. */
	Collection<BatchId> getBatchIds();
}
