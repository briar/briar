package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;

class AckImpl implements Ack {

	private final Collection<BatchId> acked;

	AckImpl(Collection<BatchId> acked) {
		this.acked = acked;
	}

	public Collection<BatchId> getBatchIds() {
		return acked;
	}
}
