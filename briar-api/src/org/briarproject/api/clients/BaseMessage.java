package org.briarproject.api.clients;

import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseMessage {

	private final Message message;
	private final MessageId parent;

	public BaseMessage(@NotNull Message message, @Nullable MessageId parent) {
		this.message = message;
		this.parent = parent;
	}

	@NotNull
	public Message getMessage() {
		return message;
	}

	@Nullable
	public MessageId getParent() {
		return parent;
	}

}
