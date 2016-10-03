package org.briarproject.api.clients;

import org.briarproject.api.sync.MessageId;

public abstract class BaseMessageHeader {

	private final MessageId id;
	private final long timestamp;
	private final boolean local, read, sent, seen;

	public BaseMessageHeader(MessageId id, long timestamp, boolean local,
			boolean read, boolean sent, boolean seen) {

		this.id = id;
		this.timestamp = timestamp;
		this.local = local;
		this.read = read;
		this.sent = sent;
		this.seen = seen;
	}

	public MessageId getId() {
		return id;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public boolean isLocal() {
		return local;
	}

	public boolean isRead() {
		return read;
	}

	public boolean isSent() {
		return sent;
	}

	public boolean isSeen() {
		return seen;
	}
}
