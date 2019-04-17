package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public enum PendingContactState {

	WAITING_FOR_CONNECTION(0),
	CONNECTED(1),
	ADDING_CONTACT(2),
	FAILED(3);

	private final int value;

	PendingContactState(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static PendingContactState fromValue(int value) {
		for (PendingContactState s : values()) if (s.value == value) return s;
		throw new IllegalArgumentException();
	}
}
