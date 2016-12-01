package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;

import static org.briarproject.bramble.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;

/**
 * Contains transport keys for receiving streams from a given contact over a
 * given transport in a given rotation period.
 */
public class IncomingKeys {

	private final SecretKey tagKey, headerKey;
	private final long rotationPeriod, windowBase;
	private final byte[] windowBitmap;

	public IncomingKeys(SecretKey tagKey, SecretKey headerKey,
			long rotationPeriod) {
		this(tagKey, headerKey, rotationPeriod, 0,
				new byte[REORDERING_WINDOW_SIZE / 8]);
	}

	public IncomingKeys(SecretKey tagKey, SecretKey headerKey,
			long rotationPeriod, long windowBase, byte[] windowBitmap) {
		this.tagKey = tagKey;
		this.headerKey = headerKey;
		this.rotationPeriod = rotationPeriod;
		this.windowBase = windowBase;
		this.windowBitmap = windowBitmap;
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

	public long getWindowBase() {
		return windowBase;
	}

	public byte[] getWindowBitmap() {
		return windowBitmap;
	}
}