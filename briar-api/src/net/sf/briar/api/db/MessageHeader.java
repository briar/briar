package net.sf.briar.api.db;

import net.sf.briar.api.messaging.AuthorId;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.MessageId;

public class MessageHeader {

	private final MessageId id, parent;
	private final GroupId group;
	private final AuthorId author;
	private final String subject;
	private final long timestamp;
	private final boolean read, starred;

	public MessageHeader(MessageId id, MessageId parent, GroupId group,
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

	/** Returns the message's unique identifier. */
	public MessageId getId() {
		return id;
	}

	/**
	 * Returns the message's parent, or null if this is the first message in a
	 * thread.
	 */
	public MessageId getParent() {
		return parent;
	}

	/**
	 * Returns the ID of the group to which the message belongs, or null if
	 * this is a private message.
	 */
	public GroupId getGroup() {
		return group;
	}

	/**
	 * Returns the ID of the message's author, or null if this is an
	 * anonymous message.
	 */
	public AuthorId getAuthor() {
		return author;
	}

	/** Returns the message's subject line. */
	public String getSubject() {
		return subject;
	}

	/** Returns the timestamp created by the message's author. */
	public long getTimestamp() {
		return timestamp;
	}

	/** Returns true if the message has been read. */
	public boolean getRead() {
		return read;
	}

	/** Returns true if the message has been starred. */
	public boolean getStarred() {
		return starred;
	}
}
