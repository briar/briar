package org.briarproject.bramble.api.crypto;

public interface CryptoConstants {

	/**
	 * The maximum length of an agreement public key in bytes.
	 */
	int MAX_AGREEMENT_PUBLIC_KEY_BYTES = 32;

	/**
	 * The key type for agreement key pairs.
	 */
	String KEY_TYPE_AGREEMENT = "Curve25519";

	/**
	 * The maximum length of a signature public key in bytes.
	 */
	int MAX_SIGNATURE_PUBLIC_KEY_BYTES = 32;

	/**
	 * The key type for signature key pairs.
	 */
	String KEY_TYPE_SIGNATURE = "Ed25519";

	/**
	 * The maximum length of a signature in bytes.
	 */
	int MAX_SIGNATURE_BYTES = 64;

	/**
	 * The length of a MAC in bytes.
	 */
	int MAC_BYTES = SecretKey.LENGTH;

}
