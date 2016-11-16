package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;

enum InviteeState implements State {

	START(0), INVITED(1), ACCEPTED(2), JOINED(3), LEFT(4), DISSOLVED(5),
	ERROR(6);

	private final int value;

	InviteeState(int value) {
		this.value = value;
	}

	@Override
	public int getValue() {
		return value;
	}

	static InviteeState fromValue(int value) throws FormatException {
		for (InviteeState s : values()) if (s.value == value) return s;
		throw new FormatException();
	}
}
