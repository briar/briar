package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
enum CreatorState implements State {

	START(0), INVITED(1), JOINED(2), LEFT(3), DISSOLVED(4), ERROR(5);

	private final int value;

	CreatorState(int value) {
		this.value = value;
	}

	@Override
	public int getValue() {
		return value;
	}

	static CreatorState fromValue(int value) throws FormatException {
		for (CreatorState s : values()) if (s.value == value) return s;
		throw new FormatException();
	}
}
