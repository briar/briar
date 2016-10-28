package org.briarproject.android.threaded;

import org.briarproject.api.clients.MessageTree.MessageNode;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.MessageId;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.android.threaded.ThreadItemAdapter.UNDEFINED;

@NotThreadSafe
public abstract class ThreadItem implements MessageNode {

	private final MessageId messageId;
	private final MessageId parentId;
	private final String text;
	private final long timestamp;
	private final Author author;
	private final Status status;
	private int level = UNDEFINED;
	private boolean isShowingDescendants = true;
	private int descendantCount = 0;
	private boolean isRead;

	public ThreadItem(MessageId messageId, MessageId parentId, String text,
			long timestamp, Author author, Status status, boolean isRead) {
		this.messageId = messageId;
		this.parentId = parentId;
		this.text = text;
		this.timestamp = timestamp;
		this.author = author;
		this.status = status;
		this.isRead = isRead;
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

	@Override
	public long getTimestamp() {
		return timestamp;
	}

	public Author getAuthor() {
		return author;
	}

	public Status getStatus() {
		return status;
	}

	public boolean isShowingDescendants() {
		return isShowingDescendants;
	}

	@Override
	public void setLevel(int level) {
		this.level = level;
	}

	public void setShowingDescendants(boolean showingDescendants) {
		this.isShowingDescendants = showingDescendants;
	}

	public boolean isRead() {
		return isRead;
	}

	public void setRead(boolean read) {
		isRead = read;
	}

	public boolean hasDescendants() {
		return descendantCount > 0;
	}

	@Override
	public void setDescendantCount(int descendantCount) {
		this.descendantCount = descendantCount;
	}

}
