package org.briarproject.api.crypto;

import java.security.GeneralSecurityException;

/** An authenticated cipher that supports additional authenticated data. */
public interface AuthenticatedCipher {

	/**
	 * Initializes this cipher with a key, an initialisation vector (IV) and
	 * additional authenticated data (AAD).
	 */
	void init(boolean encrypt, SecretKey key, byte[] iv, byte[] aad)
			throws GeneralSecurityException;

	/** Encrypts or decrypts data in a single-part operation. */
	int process(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException;

	/** Returns the length of the message authentication code (MAC) in bytes. */
	int getMacLength();

	/** Returns the block size of the cipher in bytes. */
	int getBlockSize();
}
