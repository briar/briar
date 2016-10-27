package org.briarproject.api.messaging;

import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Message;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PrivateMessage {

	private final Message message;

	public PrivateMessage(Message message) {
		this.message = message;
	}

	public Message getMessage() {
		return message;
	}

}
