package net.sf.briar.protocol;

import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

/** A simple in-memory implementation of a message. */
public class MessageImpl implements Message {

	private final MessageId id, parent;
	private final GroupId group;
	private final AuthorId author;
	private final long timestamp;
	private final byte[] body;

	public MessageImpl(MessageId id, MessageId parent, GroupId group,
			AuthorId author, long timestamp, byte[] body) {
		this.id = id;
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.timestamp = timestamp;
		this.body = body;
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

	public long getTimestamp() {
		return timestamp;
	}

	public int getSize() {
		return body.length;
	}

	public byte[] getBytes() {
		return body;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Message && id.equals(((Message)o).getId());
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
