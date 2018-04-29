package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group.Visibility;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;

@Immutable
@NotNullByDefault
enum State {

	START(0, INVISIBLE),
	LOCAL_INVITED(1, INVISIBLE),
	REMOTE_INVITED(2, VISIBLE),
	SHARING(3, SHARED),
	LOCAL_LEFT(4, INVISIBLE),
	REMOTE_HANGING(5, INVISIBLE);

	private final int value;
	private final Visibility visibility;

	State(int value, Visibility visibility) {
		this.value = value;
		this.visibility = visibility;
	}

	public int getValue() {
		return value;
	}

	public Visibility getVisibility() {
		return visibility;
	}

	public boolean canInvite() {
		return this == START;
	}

	static State fromValue(int value) throws FormatException {
		for (State s : values()) if (s.value == value) return s;
		throw new FormatException();
	}

}
