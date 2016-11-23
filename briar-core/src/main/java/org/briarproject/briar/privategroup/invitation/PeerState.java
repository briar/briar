package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
enum PeerState implements State {

	START(0), AWAIT_MEMBER(1), NEITHER_JOINED(2), LOCAL_JOINED(3),
	BOTH_JOINED(4), LOCAL_LEFT(5), ERROR(6);

	private final int value;

	PeerState(int value) {
		this.value = value;
	}

	@Override
	public int getValue() {
		return value;
	}

	static PeerState fromValue(int value) throws FormatException {
		for (PeerState s : values()) if (s.value == value) return s;
		throw new FormatException();
	}
}
