package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group.Visibility;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;

@Immutable
@NotNullByDefault
enum InviteeState implements State {

	START(0, INVISIBLE),
	INVITED(1, INVISIBLE),
	ACCEPTED(2, VISIBLE),
	JOINED(3, SHARED),
	LEFT(4, INVISIBLE),
	DISSOLVED(5, INVISIBLE),
	ERROR(6, INVISIBLE);

	private final int value;
	private final Visibility visibility;

	InviteeState(int value, Visibility visibility) {
		this.value = value;
		this.visibility = visibility;
	}

	@Override
	public int getValue() {
		return value;
	}

	@Override
	public Visibility getVisibility() {
		return visibility;
	}

	@Override
	public boolean isAwaitingResponse() {
		return this == INVITED;
	}

	static InviteeState fromValue(int value) throws FormatException {
		for (InviteeState s : values()) if (s.value == value) return s;
		throw new FormatException();
	}
}
