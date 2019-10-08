package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
enum IntroducerState implements State {

	START(0),
	AWAIT_RESPONSES(1),
	AWAIT_RESPONSE_A(2), AWAIT_RESPONSE_B(3),
	A_DECLINED(4), B_DECLINED(5),
	AWAIT_AUTHS(6),
	AWAIT_AUTH_A(7), AWAIT_AUTH_B(8),
	AWAIT_ACTIVATES(9),
	AWAIT_ACTIVATE_A(10), AWAIT_ACTIVATE_B(11);

	private final int value;

	IntroducerState(int value) {
		this.value = value;
	}

	@Override
	public int getValue() {
		return value;
	}

	@Override
	public boolean isComplete() {
		return this == START;
	}

	static IntroducerState fromValue(int value) throws FormatException {
		for (IntroducerState s : values()) if (s.value == value) return s;
		throw new FormatException();
	}

}
