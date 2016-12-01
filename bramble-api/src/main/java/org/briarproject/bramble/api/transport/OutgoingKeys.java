package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;

/**
 * Contains transport keys for sending streams to a given contact over a given
 * transport in a given rotation period.
 */
public class OutgoingKeys {

	private final SecretKey tagKey, headerKey;
	private final long rotationPeriod, streamCounter;

	public OutgoingKeys(SecretKey tagKey, SecretKey headerKey,
			long rotationPeriod) {
		this(tagKey, headerKey, rotationPeriod, 0);
	}

	public OutgoingKeys(SecretKey tagKey, SecretKey headerKey,
			long rotationPeriod, long streamCounter) {
		this.tagKey = tagKey;
		this.headerKey = headerKey;
		this.rotationPeriod = rotationPeriod;
		this.streamCounter = streamCounter;
	}

	public SecretKey getTagKey() {
		return tagKey;
	}

	public SecretKey getHeaderKey() {
		return headerKey;
	}

	public long getRotationPeriod() {
		return rotationPeriod;
	}

	public long getStreamCounter() {
		return streamCounter;
	}
}