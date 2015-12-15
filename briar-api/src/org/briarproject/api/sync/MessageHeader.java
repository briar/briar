package org.briarproject.api.sync;

import org.briarproject.api.Author;

public class MessageHeader {

	public enum State { STORED, SENT, DELIVERED }

	private final MessageId id, parent;
	private final GroupId groupId;
	private final Author author;
	private final Author.Status authorStatus;
	private final String contentType;
	private final long timestamp;
	private final boolean local, read;
	private final State status;

	public MessageHeader(MessageId id, MessageId parent, GroupId groupId,
			Author author, Author.Status authorStatus, String contentType,
			long timestamp, boolean local, boolean read, State status) {
		this.id = id;
		this.parent = parent;
		this.groupId = groupId;
		this.author = author;
		this.authorStatus = authorStatus;
		this.contentType = contentType;
		this.timestamp = timestamp;
		this.local = local;
		this.read = read;
		this.status = status;
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
	 * Returns the unique identifier of the group to which the message belongs.
	 */
	public GroupId getGroupId() {
		return groupId;
	}

	/**
	 * Returns the message's author, or null if this is an  anonymous message.
	 */
	public Author getAuthor() {
		return author;
	}

	/**  Returns the status of the message's author. */
	public Author.Status getAuthorStatus() {
		return authorStatus;
	}

	/** Returns the message's content type. */
	public String getContentType() {
		return contentType;
	}

	/** Returns the timestamp created by the message's author. */
	public long getTimestamp() {
		return timestamp;
	}

	/** Returns true if the message was locally generated. */
	public boolean isLocal() {
		return local;
	}

	/** Returns true if the message has been read. */
	public boolean isRead() {
		return read;
	}

	/**
	 * Returns message status. (This only applies to locally generated private messages.)
	 */
	public State getStatus() {
		return status;
	}
}
