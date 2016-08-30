package org.briarproject.api.forum;

import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

public class ForumPost {

	private final Message message;
	private final MessageId parent;
	private final Author author;

	public ForumPost(Message message, MessageId parent, Author author) {
		this.message = message;
		this.parent = parent;
		this.author = author;
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
}
