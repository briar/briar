package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;

/** A simple in-memory implementation of a batch. */
class BatchImpl implements Batch {

	private final BatchId id;
	private final Collection<Message> messages;

	BatchImpl(BatchId id, Collection<Message> messages) {
		this.id = id;
		this.messages = messages;
	}

	public BatchId getId() {
		return id;
	}

	public Collection<Message> getMessages() {
		return messages;
	}
}
