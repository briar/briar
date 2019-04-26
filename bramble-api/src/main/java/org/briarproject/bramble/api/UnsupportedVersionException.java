package org.briarproject.bramble.api;

/**
 * Thrown when data being parsed uses a protocol or format version that is not
 * supported.
 */
public class UnsupportedVersionException extends FormatException {

	private final boolean tooOld;

	public UnsupportedVersionException(boolean tooOld) {
		this.tooOld = tooOld;
	}

	public boolean isTooOld() {
		return tooOld;
	}
}
