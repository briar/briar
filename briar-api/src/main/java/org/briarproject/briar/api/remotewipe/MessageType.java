package org.briarproject.briar.api.remotewipe;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public enum MessageType {

	SETUP(0), WIPE(1), REVOKE(2), CONFIRM(3);

	private final int value;

	MessageType(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static MessageType fromValue(int value) throws
			FormatException {
		for (MessageType m : values()) if (m.value == value) return m;
		throw new FormatException();
	}
}
