package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

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

	public MessageImpl(MessageId id, MessageId parent, GroupId group,
			AuthorId author, String subject, long timestamp, byte[] raw) {
		this.id = id;
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.subject = subject;
		this.timestamp = timestamp;
		this.raw = raw;
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

	public int getLength() {
		return raw.length;
	}

	public byte[] getSerialisedBytes() {
		return raw;
	}

	public InputStream getSerialisedStream() {
		return new ByteArrayInputStream(raw);
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
