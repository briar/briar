package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
enum IntroduceeState implements State {

	START(0),
	AWAIT_RESPONSES(1),
	LOCAL_DECLINED(2),
	LOCAL_ACCEPTED(3),
	REMOTE_ACCEPTED(4),
	AWAIT_AUTH(5),
	AWAIT_ACTIVATE(6);

	private final int value;

	IntroduceeState(int value) {
		this.value = value;
	}

	@Override
	public int getValue() {
		return value;
	}

	static IntroduceeState fromValue(int value) throws FormatException {
		for (IntroduceeState s : values()) if (s.value == value) return s;
		throw new FormatException();
	}

}
