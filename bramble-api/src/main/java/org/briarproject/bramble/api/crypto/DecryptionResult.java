package org.briarproject.bramble.api.crypto;

/**
 * The result of a password-based decryption operation.
 */
public enum DecryptionResult {

	/**
	 * Decryption succeeded.
	 */
	SUCCESS,

	/**
	 * Decryption failed because the format of the ciphertext was invalid.
	 */
	INVALID_CIPHERTEXT,

	/**
	 * Decryption failed because the {@link KeyStrengthener} used for
	 * encryption was not available for decryption.
	 */
	KEY_STRENGTHENER_ERROR,

	/**
	 * Decryption failed because the password used for decryption did not match
	 * the password used for encryption.
	 */
	INVALID_PASSWORD
}
