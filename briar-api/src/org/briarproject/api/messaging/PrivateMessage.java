package org.briarproject.api.messaging;

import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

public class PrivateMessage {

	private final Message message;
	private final MessageId parent;
	private final String contentType;

	public PrivateMessage(Message message, MessageId parent,
			String contentType) {
		this.message = message;
		this.parent = parent;
		this.contentType = contentType;
	}

	public Message getMessage() {
		return message;
	}

	public MessageId getParent() {
		return parent;
	}

	public String getContentType() {
		return contentType;
	}
}
