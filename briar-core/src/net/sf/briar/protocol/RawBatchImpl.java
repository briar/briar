package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.RawBatch;

class RawBatchImpl implements RawBatch {

	private final BatchId id;
	private final Collection<byte[]> messages;

	RawBatchImpl(BatchId id, Collection<byte[]> messages) {
		this.id = id;
		this.messages = messages;
	}

	public BatchId getId() {
		return id;
	}

	public Collection<byte[]> getMessages() {
		return messages;
	}
}
