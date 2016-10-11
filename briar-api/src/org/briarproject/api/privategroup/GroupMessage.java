package org.briarproject.api.privategroup;

import org.briarproject.api.clients.BaseMessage;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GroupMessage extends BaseMessage {

	private final Author author;

	public GroupMessage(@NotNull Message message, @Nullable MessageId parent,
			@NotNull Author author) {
		super(message, parent);
		this.author = author;
	}

	public Author getAuthor() {
		return author;
	}

}
