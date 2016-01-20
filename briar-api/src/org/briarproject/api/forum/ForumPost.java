package org.briarproject.api.forum;

import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

public class ForumPost {

	private final Message message;
	private final MessageId parent;
	private final Author author;
	private final String contentType;

	public ForumPost(Message message, MessageId parent, Author author,
			String contentType) {
		this.message = message;
		this.parent = parent;
		this.author = author;
		this.contentType = contentType;
	}

	public Message getMessage() {
		return message;
	}

	public MessageId getParent() {
		return parent;
	}

	public Author getAuthor() {
		return author;
	}

	public String getContentType() {
		return contentType;
	}
}
