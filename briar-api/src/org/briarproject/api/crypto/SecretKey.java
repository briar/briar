package org.briarproject.api.crypto;

/** A secret key used for encryption and/or authentication. */
public class SecretKey {

	public static final int LENGTH = 32; // Bytes

	private final byte[] key;

	public SecretKey(byte[] key) {
		if(key.length != LENGTH) throw new IllegalArgumentException();
		this.key = key;
	}

	public byte[] getBytes() {
		return key;
	}
}
