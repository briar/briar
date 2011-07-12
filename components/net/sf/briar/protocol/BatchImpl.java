package net.sf.briar.protocol;

import java.util.List;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;

/** A simple in-memory implementation of a batch. */
class BatchImpl implements Batch {

	private final BatchId id;
	private final long size;
	private final List<Message> messages;
	private final byte[] signature;

	BatchImpl(BatchId id, long size, List<Message> messages, byte[] signature) {
		this.id = id;
		this.size = size;
		this.messages = messages;
		this.signature = signature;
	}

	public BatchId getId() {
		return id;
	}

	public long getSize() {
		return size;
	}

	public Iterable<Message> getMessages() {
		return messages;
	}

	public byte[] getSignature() {
		return signature;
	}
}
