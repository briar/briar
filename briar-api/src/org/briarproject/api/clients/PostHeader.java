package org.briarproject.api.clients;

import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.MessageId;

public abstract class PostHeader {

	private final MessageId id;
	private final MessageId parentId;
	private final long timestamp;
	private final Author author;
	private final Status authorStatus;
	private final boolean read;

	public PostHeader(MessageId id, MessageId parentId, long timestamp,
			Author author, Status authorStatus, boolean read) {
		this.id = id;
		this.parentId = parentId;
		this.timestamp = timestamp;
		this.author = author;
		this.authorStatus = authorStatus;
		this.read = read;
	}

	public MessageId getId() {
		return id;
	}

	public Author getAuthor() {
		return author;
	}

	public Status getAuthorStatus() {
		return authorStatus;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public boolean isRead() {
		return read;
	}

	public MessageId getParentId() {
		return parentId;
	}
}
