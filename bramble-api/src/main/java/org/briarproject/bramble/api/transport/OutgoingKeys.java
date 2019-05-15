package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * Contains transport keys for sending streams to a given contact or pending
 * contact over a given transport in a given time period.
 */
@Immutable
@NotNullByDefault
public class OutgoingKeys {

	private final SecretKey tagKey, headerKey;
	private final long timePeriod, streamCounter;
	private final boolean active;

	public OutgoingKeys(SecretKey tagKey, SecretKey headerKey,
			long timePeriod, boolean active) {
		this(tagKey, headerKey, timePeriod, 0, active);
	}

	public OutgoingKeys(SecretKey tagKey, SecretKey headerKey,
			long timePeriod, long streamCounter, boolean active) {
		this.tagKey = tagKey;
		this.headerKey = headerKey;
		this.timePeriod = timePeriod;
		this.streamCounter = streamCounter;
		this.active = active;
	}

	public SecretKey getTagKey() {
		return tagKey;
	}

	public SecretKey getHeaderKey() {
		return headerKey;
	}

	public long getTimePeriod() {
		return timePeriod;
	}

	public long getStreamCounter() {
		return streamCounter;
	}

	public boolean isActive() {
		return active;
	}
}