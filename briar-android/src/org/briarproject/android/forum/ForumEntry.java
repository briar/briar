package org.briarproject.android.forum;

import org.briarproject.api.clients.MessageTree;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.MessageId;

public class ForumEntry implements MessageTree.MessageNode {

	public final static int LEVEL_UNDEFINED = -1;

	private final MessageId messageId;
	private final MessageId parentId;
	private final String text;
	private final long timestamp;
	private final Author author;
	private Status status;
	private int level = LEVEL_UNDEFINED;
	private boolean isShowingDescendants = true;
	private boolean isRead = true;

	ForumEntry(ForumPostHeader h, String text) {
		this(h.getId(), h.getParentId(), text, h.getTimestamp(), h.getAuthor(),
				h.getAuthorStatus());
		this.isRead = h.isRead();
	}

	public ForumEntry(MessageId messageId, MessageId parentId, String text,
			long timestamp, Author author, Status status) {
		this.messageId = messageId;
		this.parentId = parentId;
		this.text = text;
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

	@Override
	public MessageId getId() {
		return messageId;
	}

	@Override
	public MessageId getParentId() {
		return parentId;
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

	void setLevel(int level) {
		this.level = level;
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
