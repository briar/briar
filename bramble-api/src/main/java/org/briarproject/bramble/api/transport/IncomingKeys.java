package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;

/**
 * Contains transport keys for receiving streams from a given contact or
 * pending contact over a given transport in a given time period.
 */
@Immutable
@NotNullByDefault
public class IncomingKeys {

	private final SecretKey tagKey, headerKey;
	private final long timePeriod, windowBase;
	private final byte[] windowBitmap;

	public IncomingKeys(SecretKey tagKey, SecretKey headerKey,
			long timePeriod) {
		this(tagKey, headerKey, timePeriod, 0,
				new byte[REORDERING_WINDOW_SIZE / 8]);
	}

	public IncomingKeys(SecretKey tagKey, SecretKey headerKey,
			long timePeriod, long windowBase, byte[] windowBitmap) {
		this.tagKey = tagKey;
		this.headerKey = headerKey;
		this.timePeriod = timePeriod;
		this.windowBase = windowBase;
		this.windowBitmap = windowBitmap;
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

	public long getWindowBase() {
		return windowBase;
	}

	public byte[] getWindowBitmap() {
		return windowBitmap;
	}
}