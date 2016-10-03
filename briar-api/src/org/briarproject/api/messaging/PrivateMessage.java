package org.briarproject.api.messaging;

import org.briarproject.api.clients.BaseMessage;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrivateMessage extends BaseMessage {

	private final String contentType;

	public PrivateMessage(@NotNull Message message, @Nullable MessageId parent,
			@NotNull String contentType) {
		super(message, parent);
		this.contentType = contentType;
	}

	public String getContentType() {
		return contentType;
	}

}
