package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;

enum CreatorState implements State {

	START(0), INVITED(1), INVITEE_JOINED(2), INVITEE_LEFT(3), DISSOLVED(4),
	ERROR(5);

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
