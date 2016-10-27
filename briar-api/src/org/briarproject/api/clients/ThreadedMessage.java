package org.briarproject.api.clients;

import org.briarproject.api.identity.Author;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class ThreadedMessage extends PrivateMessage {

	@Nullable
	private final MessageId parent;
	private final Author author;

	public ThreadedMessage(Message message, @Nullable MessageId parent,
			Author author) {
		super(message);
		this.parent = parent;
		this.author = author;
	}

	@Nullable
	public MessageId getParent() {
		return parent;
	}

	public Author getAuthor() {
		return author;
	}

}
