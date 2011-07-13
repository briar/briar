package net.sf.briar.protocol;

import java.util.List;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;

/** A simple in-memory implementation of a batch. */
class BatchImpl implements Batch {

	private final BatchId id;
	private final List<Message> messages;

	BatchImpl(BatchId id, List<Message> messages) {
		this.id = id;
		this.messages = messages;
	}

	public BatchId getId() {
		return id;
	}

	public Iterable<Message> getMessages() {
		return messages;
	}
}
