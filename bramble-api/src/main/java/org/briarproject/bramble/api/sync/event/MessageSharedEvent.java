package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a message is shared.
 */
@Immutable
@NotNullByDefault
public class MessageSharedEvent extends Event {

	private final MessageId messageId;

	public MessageSharedEvent(MessageId message) {
		this.messageId = message;
	}

	public MessageId getMessageId() {
		return messageId;
	}
}
