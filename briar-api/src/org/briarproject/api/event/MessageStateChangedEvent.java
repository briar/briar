package org.briarproject.api.event;

import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.ValidationManager;
import static org.briarproject.api.sync.ValidationManager.State;

/**
 * An event that is broadcast when a message state changed.
 */
public class MessageStateChangedEvent extends Event {

	private final Message message;
	private final ClientId clientId;
	private final boolean local;
	private final State state;

	public MessageStateChangedEvent(Message message, ClientId clientId,
			boolean local, State state) {
		this.message = message;
		this.clientId = clientId;
		this.local = local;
		this.state = state;
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

	public State getState() {
		return state;
	}

}
