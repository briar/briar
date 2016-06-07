package org.briarproject.android.blogs;

import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.MessageId;

// This class is not thread-safe
class BlogPostItem {

	private final BlogPostHeader header;
	private final byte[] body;
	private boolean read;

	BlogPostItem(BlogPostHeader header, byte[] body) {
		this.header = header;
		this.body = body;
		read = header.isRead();
	}

	public MessageId getId() {
		return header.getId();
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
}
