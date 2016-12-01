package org.briarproject.briar.api.blog;

public enum MessageType {

	POST(0),
	COMMENT(1),
	WRAPPED_POST(2),
	WRAPPED_COMMENT(3);

	int value;

	MessageType(int value) {
		this.value = value;
	}

	public static MessageType valueOf(int value) {
		switch (value) {
			case 0:
				return POST;
			case 1:
				return COMMENT;
			case 2:
				return WRAPPED_POST;
			case 3:
				return WRAPPED_COMMENT;
			default:
				throw new IllegalArgumentException();
		}
	}

	public int getInt() {
		return value;
	}
}