package org.briarproject.sync;

import org.briarproject.api.Author;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.sync.MessagingConstants.MAX_BODY_LENGTH;

/** A simple in-memory implementation of a message. */
class MessageImpl implements Message {

	private final MessageId id, parent;
	private final Group group;
	private final Author author;
	private final String contentType;
	private final long timestamp;
	private final byte[] raw;
	private final int bodyStart, bodyLength;

	public MessageImpl(MessageId id, MessageId parent, Group group,
			Author author, String contentType, long timestamp,
			byte[] raw, int bodyStart, int bodyLength) {
		if (bodyStart + bodyLength > raw.length)
			throw new IllegalArgumentException();
		if (bodyLength > MAX_BODY_LENGTH)
			throw new IllegalArgumentException();
		this.id = id;
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.contentType = contentType;
		this.timestamp = timestamp;
		this.raw = raw;
		this.bodyStart = bodyStart;
		this.bodyLength = bodyLength;
	}

	public MessageId getId() {
		return id;
	}

	public MessageId getParent() {
		return parent;
	}

	public Group getGroup() {
		return group;
	}

	public Author getAuthor() {
		return author;
	}

	public String getContentType() {
		return contentType;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public byte[] getSerialised() {
		return raw;
	}

	public int getBodyStart() {
		return bodyStart;
	}

	public int getBodyLength() {
		return bodyLength;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Message && id.equals(((Message) o).getId());
	}
}
