package org.briarproject.api.forum;

import org.briarproject.api.clients.BaseMessage;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ForumPost extends BaseMessage {

	private final Author author;

	public ForumPost(@NotNull Message message, @Nullable MessageId parent,
			@Nullable Author author) {
		super(message, parent);
		this.author = author;
	}

	@Nullable
	public Author getAuthor() {
		return author;
	}

}
