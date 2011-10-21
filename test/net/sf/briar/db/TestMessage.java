package net.sf.briar.db;

import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

class TestMessage implements Message {

	private final MessageId id, parent;
	private final GroupId group;
	private final AuthorId author;
	private final String subject;
	private final long timestamp;
	private final byte[] raw;

	public TestMessage(MessageId id, MessageId parent, GroupId group,
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

	public int getSize() {
		return raw.length;
	}

	public byte[] getBytes() {
		return raw;
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
