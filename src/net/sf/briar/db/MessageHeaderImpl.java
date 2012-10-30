package net.sf.briar.db;

import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.MessageId;

class MessageHeaderImpl implements MessageHeader {

	private final MessageId id, parent;
	private final GroupId group;
	private final AuthorId author;
	private final String subject;
	private final long timestamp;
	private final boolean read, starred;

	MessageHeaderImpl(MessageId id, MessageId parent, GroupId group,
			AuthorId author, String subject, long timestamp, boolean read,
			boolean starred) {
		this.id = id;
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.subject = subject;
		this.timestamp = timestamp;
		this.read = read;
		this.starred = starred;
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

	public boolean getRead() {
		return read;
	}

	public boolean getStarred() {
		return starred;
	}
}
