package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;

enum InviteeState implements State {

	START(0), INVITED(1), INVITEE_JOINED(2), INVITEE_LEFT(3), DISSOLVED(4),
	ERROR(5);

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
