package org.briarproject.api.event;

import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.sync.ValidationManager.State;

/**
 * An event that is broadcast when a message state changed.
 */
public class MessageStateChangedEvent extends Event {

	private final MessageId messageId;
	private final boolean local;
	private final State state;

	public MessageStateChangedEvent(MessageId messageId, boolean local,
			State state) {
		this.messageId = messageId;
		this.local = local;
		this.state = state;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public boolean isLocal() {
		return local;
	}

	public State getState() {
		return state;
	}

}
