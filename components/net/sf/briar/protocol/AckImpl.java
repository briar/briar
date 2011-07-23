package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;

class AckImpl implements Ack {

	private final Collection<BatchId> batches;

	AckImpl(Collection<BatchId> batches) {
		this.batches = batches;
	}

	public Collection<BatchId> getBatches() {
		return batches;
	}
}
