package org.briarproject.bramble.api.crypto;

/**
 * A secret key used for encryption and/or authentication.
 */
public class SecretKey {

	/**
	 * The length of a secret key in bytes.
	 */
	public static final int LENGTH = 32;

	private final byte[] key;

	public SecretKey(byte[] key) {
		if (key.length != LENGTH) throw new IllegalArgumentException();
		this.key = key;
	}

	public byte[] getBytes() {
		return key;
	}
}
