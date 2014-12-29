package org.briarproject.api.crypto;

/** A secret key used for encryption and/or authentication. */
public class SecretKey {

	private final byte[] key;

	public SecretKey(byte[] key) {
		this.key = key;
	}

	public byte[] getBytes() {
		return key;
	}
}
