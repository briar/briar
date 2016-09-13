package org.briarproject.android.forum;

import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.MessageId;

public class ForumEntry {

	private final MessageId messageId;
	private final String text;
	private final int level;
	private final long timestamp;
	private final Author author;
	private Status status;
	private boolean isShowingDescendants = true;
	private boolean isRead = true;

	ForumEntry(ForumPostHeader h, String text, int level) {
		this(h.getId(), text, level, h.getTimestamp(), h.getAuthor(),
				h.getAuthorStatus());
		this.isRead = h.isRead();
	}

	public ForumEntry(MessageId messageId, String text, int level,
			long timestamp, Author author, Status status) {
		this.messageId = messageId;
		this.text = text;
		this.level = level;
		this.timestamp = timestamp;
		this.author = author;
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

	public Author getAuthor() {
		return author;
	}

	public Status getStatus() {
		return status;
	}

	boolean isShowingDescendants() {
		return isShowingDescendants;
	}

	void setShowingDescendants(boolean showingDescendants) {
		this.isShowingDescendants = showingDescendants;
	}

	MessageId getMessageId() {
		return messageId;
	}

	public boolean isRead() {
		return isRead;
	}

	void setRead(boolean read) {
		isRead = read;
	}
}
