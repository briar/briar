package org.briarproject.crypto;

import java.security.GeneralSecurityException;

import org.briarproject.api.crypto.SecretKey;

interface AuthenticatedCipher {

	/**
	 * Initializes this cipher for encryption or decryption with a key and an
	 * initialisation vector (IV).
	 */
	void init(boolean encrypt, SecretKey key, byte[] iv)
			throws GeneralSecurityException;

	/** Encrypts or decrypts data in a single-part operation. */
	int process(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException;

	/** Returns the length of the message authentication code (MAC) in bytes. */
	int getMacBytes();
}
