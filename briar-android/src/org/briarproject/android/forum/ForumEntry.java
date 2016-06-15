package org.briarproject.android.forum;

import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.MessageId;

public class ForumEntry {

	private final MessageId messageId;
	private final String text;
	private final int level;
	private final long timestamp;
	private final String author;
	private final AuthorId authorId;
	private Status status;
	private boolean isShowingDescendants = true;
	private boolean isRead = true;

	public ForumEntry(ForumPostHeader h, String text, int level) {
		this(h.getId(), text, level, h.getTimestamp(), h.getAuthor().getName(),
				h.getAuthor().getId(), h.getAuthorStatus());
		this.isRead = h.isRead();
	}

	public ForumEntry(MessageId messageId, String text, int level,
			long timestamp, String author, AuthorId authorId, Status status) {
		this.messageId = messageId;
		this.text = text;
		this.level = level;
		this.timestamp = timestamp;
		this.author = author;
		this.authorId = authorId;
		this.status = status;
	}

	public String getText() {
		return text;
	}

	public int getLevel() {
		return level;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getAuthor() {
		return author;
	}

	public AuthorId getAuthorId() {
		return authorId;
	}

	public Status getStatus() {
		return status;
	}

	public boolean isShowingDescendants() {
		return isShowingDescendants;
	}

	public void setShowingDescendants(boolean showingDescendants) {
		this.isShowingDescendants = showingDescendants;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public boolean isRead() {
		return isRead;
	}

	public void setRead(boolean read) {
		isRead = read;
	}
}
