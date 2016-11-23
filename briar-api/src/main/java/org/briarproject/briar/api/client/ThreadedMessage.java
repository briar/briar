package org.briarproject.briar.api.client;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.PrivateMessage;

import javax.annotation.Nullable;
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
