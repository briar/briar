package org.briarproject.api.crypto;

import java.security.GeneralSecurityException;

public interface AuthenticatedCipher {

	/**
	 * Initializes this cipher with a key and an initialisation vector (IV).
	 */
	void init(boolean encrypt, SecretKey key, byte[] iv)
			throws GeneralSecurityException;

	/** Encrypts or decrypts data in a single-part operation. */
	int process(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException;

	/** Returns the length of the message authentication code (MAC) in bytes. */
	int getMacBytes();

	/** Returns the block size of the cipher in bytes. */
	int getBlockBytes();
}
