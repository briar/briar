package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_BODY_LENGTH;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

/** A simple in-memory implementation of a message. */
class MessageImpl implements Message {

	private final MessageId id, parent;
	private final GroupId group;
	private final AuthorId author;
	private final String subject;
	private final long timestamp;
	private final byte[] raw;
	private final int bodyStart, bodyLength;

	public MessageImpl(MessageId id, MessageId parent, GroupId group,
			AuthorId author, String subject, long timestamp, byte[] raw,
			int bodyStart, int bodyLength) {
		if(bodyStart + bodyLength > raw.length)
			throw new IllegalArgumentException();
		if(bodyLength > MAX_BODY_LENGTH)
			throw new IllegalArgumentException();
		this.id = id;
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.subject = subject;
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

	public GroupId getGroup() {
		return group;
	}

	public AuthorId getAuthor() {
		return author;
	}

	public String getSubject() {
		return subject;
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
	public boolean equals(Object o) {
		return o instanceof Message && id.equals(((Message) o).getId());
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
