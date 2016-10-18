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
		switch (value) {
			case 0:
				return NEW_MEMBER;
			case 1:
				return JOIN;
			case 2:
				return POST;
			default:
				throw new IllegalArgumentException();
		}
	}

	public int getInt() {
		return value;
	}
}