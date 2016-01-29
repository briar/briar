package org.briarproject.api.event;

import org.briarproject.api.sync.Message;

/** An event that is broadcast when a message is shared. */
public class MessageSharedEvent extends Event {

	private final Message message;

	public MessageSharedEvent(Message message) {
		this.message = message;
	}

	public Message getMessage() {
		return message;
	}
}
