package net.sf.briar.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;

class BatchImpl implements Batch {

	private final List<Message> messages = new ArrayList<Message>();
	private BatchId id = null;
	private long size = 0L;

	public void seal() {
		// FIXME: Calculate batch ID
		byte[] b = new byte[BatchId.LENGTH];
		new Random().nextBytes(b);
		id = new BatchId(b);
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

	public void addMessage(Message m) {
		messages.add(m);
		size += m.getSize();
	}
}
