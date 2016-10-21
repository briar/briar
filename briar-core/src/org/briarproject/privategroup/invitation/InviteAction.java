package org.briarproject.privategroup.invitation;

import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class InviteAction {

	@Nullable
	private final String message;
	private final long timestamp;
	private final byte[] signature;

	InviteAction(@Nullable String message, long timestamp, byte[] signature) {
		this.message = message;
		this.timestamp = timestamp;
		this.signature = signature;
	}

	@Nullable
	String getMessage() {
		return message;
	}

	long getTimestamp() {
		return timestamp;
	}

	byte[] getSignature() {
		return signature;
	}
}
