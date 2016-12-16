package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
enum State {

	START(0), LOCAL_INVITED(1), REMOTE_INVITED(2), SHARING(3), LOCAL_LEFT(4),
	REMOTE_LEFT(5),	REMOTE_HANGING(6), ERROR(7);

	private final int value;

	State(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public boolean canInvite() {
		return this == START || this == REMOTE_LEFT;
	}

	static State fromValue(int value) throws FormatException {
		for (State s : values()) if (s.value == value) return s;
		throw new FormatException();
	}

}
