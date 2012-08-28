package net.sf.briar.api.crypto;

import java.security.InvalidKeyException;
import java.security.Key;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

/**
 * A wrapper for a provider-dependent cipher class, since javax.crypto.Cipher
 * doesn't support additional authenticated data until Java 7.
 */
public interface AuthenticatedCipher {

	/**
	 * Initializes this cipher with a key, an initialisation vector (IV) and
	 * additional authenticated data (AAD).
	 */
	void init(int opmode, Key key, byte[] iv, byte[] aad)
			throws InvalidKeyException;

	/** Encrypts or decrypts data in a single-part operation. */
	int doFinal(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws IllegalBlockSizeException,
			BadPaddingException;

	/** Returns the length of the message authenticated code (MAC) in bytes. */
	int getMacLength();
}
