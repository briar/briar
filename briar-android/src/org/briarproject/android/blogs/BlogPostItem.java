package org.briarproject.android.blogs;

import android.support.annotation.NonNull;

import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

// This class is not thread-safe
class BlogPostItem implements Comparable<BlogPostItem> {

	private final GroupId groupId;
	private final BlogPostHeader header;
	private final byte[] body;
	private boolean read;

	BlogPostItem(GroupId groupId, BlogPostHeader header, byte[] body) {
		this.groupId = groupId;
		this.header = header;
		this.body = body;
		read = header.isRead();
	}

	public MessageId getId() {
		return header.getId();
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public String getTitle() {
		return header.getTitle();
	}

	public byte[] getBody() {
		return body;
	}

	public long getTimestamp() {
		return header.getTimestamp();
	}

	public long getTimeReceived() {
		return header.getTimeReceived();
	}

	public Author getAuthor() {
		return header.getAuthor();
	}

	Status getAuthorStatus() {
		return header.getAuthorStatus();
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public boolean isRead() {
		return read;
	}

	@Override
	public int compareTo(@NonNull BlogPostItem other) {
		if (this == other) return 0;
		// The blog with the newest message comes first
		long aTime = getTimeReceived(), bTime = other.getTimeReceived();
		if (aTime > bTime) return -1;
		if (aTime < bTime) return 1;
		// Break ties by post title
		if (getTitle() != null && other.getTitle() != null) {
			return String.CASE_INSENSITIVE_ORDER
					.compare(getTitle(), other.getTitle());
		}
		return 0;
	}
}
