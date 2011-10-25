package net.sf.briar.protocol;

import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.MessageHeader;
import net.sf.briar.api.protocol.MessageId;

class MessageHeaderImpl implements MessageHeader {

	private final MessageId id, parent;
	private final GroupId group;
	private final AuthorId author;
	private final String subject;
	private final long timestamp;

	MessageHeaderImpl(MessageId id, MessageId parent, GroupId group,
			AuthorId author, String subject, long timestamp) {
		this.id = id;
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.subject = subject;
		this.timestamp = timestamp;
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
}
