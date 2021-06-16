package org.briarproject.bramble.transport.agreement;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
enum State {

	/**
	 * We've sent a key message and are awaiting the contact's key message.
	 */
	AWAIT_KEY(0),

	/**
	 * We've exchanged key messages, derived the transport keys and sent an
	 * activate message, and now we're awaiting the contact's activate message.
	 */
	AWAIT_ACTIVATE(1),

	/**
	 * We've exchanged key messages and activate messages, and have derived and
	 * activated the transport keys. This is the end state.
	 */
	ACTIVATED(2);

	private final int value;

	State(int value) {
		this.value = value;
	}

	int getValue() {
		return value;
	}

	static State fromValue(int value) throws FormatException {
		for (State s : values()) if (s.value == value) return s;
		throw new FormatException();
	}
}
