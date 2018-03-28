package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.OutgoingKeys;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class MutableOutgoingKeys {

	private final SecretKey tagKey, headerKey;
	private final long rotationPeriod;
	private long streamCounter;
	private boolean active;

	MutableOutgoingKeys(OutgoingKeys out) {
		tagKey = out.getTagKey();
		headerKey = out.getHeaderKey();
		rotationPeriod = out.getRotationPeriod();
		streamCounter = out.getStreamCounter();
		active = out.isActive();
	}

	OutgoingKeys snapshot() {
		return new OutgoingKeys(tagKey, headerKey, rotationPeriod,
				streamCounter, active);
	}

	SecretKey getTagKey() {
		return tagKey;
	}

	SecretKey getHeaderKey() {
		return headerKey;
	}

	long getRotationPeriod() {
		return rotationPeriod;
	}

	long getStreamCounter() {
		return streamCounter;
	}

	void incrementStreamCounter() {
		streamCounter++;
	}

	boolean isActive() {
		return active;
	}

	void activate() {
		active = true;
	}
}
