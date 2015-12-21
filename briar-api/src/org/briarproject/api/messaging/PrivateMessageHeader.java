package org.briarproject.api.messaging;

import org.briarproject.api.sync.MessageId;

public class PrivateMessageHeader {

	private final MessageId id;
	private final long timestamp;
	private final String contentType;
	private final boolean local, read, sent, seen;

	public PrivateMessageHeader(MessageId id, long timestamp,
			String contentType, boolean local, boolean read, boolean sent,
			boolean seen) {
		this.id = id;
		this.timestamp = timestamp;
		this.contentType = contentType;
		this.local = local;
		this.read = read;
		this.sent = sent;
		this.seen = seen;
	}

	public MessageId getId() {
		return id;
	}

	public String getContentType() {
		return contentType;
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
