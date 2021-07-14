package org.briarproject.bramble.api.sync.validation;

import org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction;

public enum MessageState {

	/**
	 * A remote message that has not yet been validated.
	 */
	UNKNOWN(0),

	/**
	 * A remote message that has failed validation, has been
	 * {@link DeliveryAction#REJECT rejected} by the local sync client, or
	 * depends on another message that has failed validation or been rejected.
	 */
	INVALID(1),

	/**
	 * A remote message that has passed validation and is awaiting delivery to
	 * the local sync client. The message will not be delivered until all its
	 * dependencies have been validated and delivered.
	 */
	PENDING(2),

	/**
	 * A local message, or a remote message that has passed validation and
	 * been delivered to the local sync client.
	 */
	DELIVERED(3);

	private final int value;

	MessageState(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static MessageState fromValue(int value) {
		for (MessageState s : values()) if (s.value == value) return s;
		throw new IllegalArgumentException();
	}
}
