package org.briarproject.briar.api.client;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorInfo.Status;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class PostHeader {

	private final MessageId id;
	@Nullable
	private final MessageId parentId;
	private final long timestamp;
	private final Author author;
	private final AuthorInfo authorInfo;
	private final boolean read;

	public PostHeader(MessageId id, @Nullable MessageId parentId,
			long timestamp, Author author, AuthorInfo authorInfo, boolean read) {
		this.id = id;
		this.parentId = parentId;
		this.timestamp = timestamp;
		this.author = author;
		this.authorInfo = authorInfo;
		this.read = read;
	}

	public MessageId getId() {
		return id;
	}

	public Author getAuthor() {
		return author;
	}

	public Status getAuthorStatus() {
		return authorInfo.getStatus();
	}

	public AuthorInfo getAuthorInfo() {
		return authorInfo;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public boolean isRead() {
		return read;
	}

	@Nullable
	public MessageId getParentId() {
		return parentId;
	}
}
