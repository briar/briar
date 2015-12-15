package org.briarproject.transport;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.transport.OutgoingKeys;

// This class is not thread-safe
class MutableOutgoingKeys {

	private final SecretKey tagKey, headerKey;
	private final long rotationPeriod;
	private long streamCounter;

	MutableOutgoingKeys(OutgoingKeys out) {
		tagKey = out.getTagKey();
		headerKey = out.getHeaderKey();
		rotationPeriod = out.getRotationPeriod();
		streamCounter = out.getStreamCounter();
	}

	OutgoingKeys snapshot() {
		return new OutgoingKeys(tagKey, headerKey, rotationPeriod,
				streamCounter);
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
}
