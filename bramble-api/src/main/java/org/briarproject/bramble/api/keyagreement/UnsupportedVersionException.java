package org.briarproject.bramble.api.keyagreement;

import java.io.IOException;

/**
 * Thrown when a QR code that has been scanned uses a protocol version that is
 * not supported.
 */
public class UnsupportedVersionException extends IOException {

	private final boolean tooOld;

	public UnsupportedVersionException(boolean tooOld) {
		this.tooOld = tooOld;
	}

	public boolean isTooOld() {
		return tooOld;
	}
}
