package org.briarproject.api.clients;

import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class BaseMessage {

	private final Message message;
	@Nullable
	private final MessageId parent;

	public BaseMessage(Message message, @Nullable MessageId parent) {
		this.message = message;
		this.parent = parent;
	}

	public Message getMessage() {
		return message;
	}

	@Nullable
	public MessageId getParent() {
		return parent;
	}

}
