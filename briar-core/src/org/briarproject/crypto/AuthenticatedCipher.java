package org.briarproject.crypto;

import org.briarproject.api.crypto.SecretKey;

import java.security.GeneralSecurityException;

interface AuthenticatedCipher {

	/**
	 * Initializes this cipher for encryption or decryption with a key and an
	 * initialisation vector (IV).
	 *
	 * @param encrypt whether we are encrypting or decrypting.
	 * @param key the key material to use.
	 * @param iv the IV.
	 * @throws GeneralSecurityException on invalid input.
	 */
	void init(boolean encrypt, SecretKey key, byte[] iv)
			throws GeneralSecurityException;

	/**
	 * Encrypts or decrypts data in a single-part operation.
	 *
	 * @param input the input byte array. If encrypting, the plaintext to be
	 *              encrypted. If decrypting, the ciphertext to be decrypted
	 *              including the MAC.
	 * @param inputOff the offset into the input array where the data to be
	 *                 processed starts.
	 * @param len the number of bytes to be processed. If decrypting, includes
	 *            the MAC length.
	 * @param output the output buffer the processed bytes go into. If
	 *               encrypting, the ciphertext including the MAC. If
	 *               decrypting, the plaintext.
	 * @param outputOff the offset into the output byte array the processed
	 *                  data starts at.
	 * @return  the number of bytes processed.
	 * @throws GeneralSecurityException on invalid input.
	 */
	int process(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException;

	/** Returns the length of the message authentication code (MAC) in bytes. */
	int getMacBytes();
}
