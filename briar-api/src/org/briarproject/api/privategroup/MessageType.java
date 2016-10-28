package org.briarproject.api.privategroup;

public enum MessageType {
	NEW_MEMBER(0),
	JOIN(1),
	POST(2);

	int value;

	MessageType(int value) {
		this.value = value;
	}

	public static MessageType valueOf(int value) {
		for (MessageType m : values()) if (m.value == value) return m;
		throw new IllegalArgumentException();
	}

	public int getInt() {
		return value;
	}
}
