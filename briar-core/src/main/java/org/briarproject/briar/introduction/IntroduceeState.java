package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
enum IntroduceeState implements State {

	START(0),
	AWAIT_RESPONSES(1),
	LOCAL_DECLINED(2),
	REMOTE_DECLINED(3),
	LOCAL_ACCEPTED(4),
	REMOTE_ACCEPTED(5),
	AWAIT_AUTH(6),
	AWAIT_ACTIVATE(7);

	private final int value;

	IntroduceeState(int value) {
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

	static IntroduceeState fromValue(int value) throws FormatException {
		for (IntroduceeState s : values()) if (s.value == value) return s;
		throw new FormatException();
	}

}
