package org.briarproject.api.event;

import org.briarproject.api.db.Metadata;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Message;

/**
 * An event that is broadcast when a message has passed or failed validation.
 */
public class MessageValidatedEvent extends Event {

	private final Message message;
	private final ClientId clientId;
	private final boolean local, valid;

	public MessageValidatedEvent(Message message, ClientId clientId,
			boolean local, boolean valid) {
		this.message = message;
		this.clientId = clientId;
		this.local = local;
		this.valid = valid;
	}

	public Message getMessage() {
		return message;
	}

	public ClientId getClientId() {
		return clientId;
	}

	public boolean isLocal() {
		return local;
	}

	public boolean isValid() {
		return valid;
	}
}
