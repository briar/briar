package net.sf.briar.api.crypto;

/**
 * Encapsulates a password. Implementations may keep the password encrypted in
 * memory to reduce the chances of writing it to the swapfile in plaintext.
 */
public interface Password {

	/**
	 * Returns the password as a character array, which should be filled with
	 * zeroes as soon as it has been used.
	 */
	char[] getPassword();
}
