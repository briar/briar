package org.briarproject.api.event;

import org.briarproject.api.sync.MessageId;

/** An event that is broadcast when a message is shared. */
public class MessageSharedEvent extends Event {

	private final MessageId messageId;

	public MessageSharedEvent(MessageId message) {
		this.messageId = message;
	}

	public MessageId getMessageId() {
		return messageId;
	}
}
