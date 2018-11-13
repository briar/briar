package org.briarproject.bramble.api.sync.validation;

public enum MessageState {

	UNKNOWN(0), INVALID(1), PENDING(2), DELIVERED(3);

	private final int value;

	MessageState(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static MessageState fromValue(int value) {
		for (MessageState s : values()) if (s.value == value) return s;
		throw new IllegalArgumentException();
	}
}
