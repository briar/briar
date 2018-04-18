package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
enum IntroducerState implements State {

	START(0),
	AWAIT_RESPONSES(1),
	AWAIT_RESPONSE_A(2), AWAIT_RESPONSE_B(3),
	AWAIT_AUTHS(4),
	AWAIT_AUTH_A(5), AWAIT_AUTH_B(6),
	AWAIT_ACTIVATES(7),
	AWAIT_ACTIVATE_A(8), AWAIT_ACTIVATE_B(9);

	private final int value;

	IntroducerState(int value) {
		this.value = value;
	}

	@Override
	public int getValue() {
		return value;
	}

	static IntroducerState fromValue(int value) throws FormatException {
		for (IntroducerState s : values()) if (s.value == value) return s;
		throw new FormatException();
	}

}
