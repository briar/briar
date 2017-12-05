package org.briarproject.bramble.api.crypto;

/**
 * Crypto operations for the key agreement protocol - see
 * https://code.briarproject.org/akwizgran/briar-spec/blob/master/protocols/BQP.md
 */
public interface KeyAgreementCrypto {

	/**
	 * Hash label for public key commitment.
	 */
	String COMMIT_LABEL = "org.briarproject.bramble.keyagreement/COMMIT";

	/**
	 * Key derivation label for confirmation record.
	 */
	String CONFIRMATION_KEY_LABEL =
			"org.briarproject.bramble.keyagreement/CONFIRMATION_KEY";

	/**
	 * MAC label for confirmation record.
	 */
	String CONFIRMATION_MAC_LABEL =
			"org.briarproject.bramble.keyagreement/CONFIRMATION_MAC";

	/**
	 * Derives a commitment to the provided public key.
	 *
	 * @param publicKey the public key
	 * @return the commitment to the provided public key.
	 */
	byte[] deriveKeyCommitment(PublicKey publicKey);

	/**
	 * Derives the content of a confirmation record.
	 *
	 * @param sharedSecret the common shared secret
	 * @param theirPayload the key exchange payload of the remote party
	 * @param ourPayload the key exchange payload of the local party
	 * @param theirPublicKey the ephemeral public key of the remote party
	 * @param ourKeyPair our ephemeral key pair of the local party
	 * @param alice true if the local party is Alice
	 * @param aliceRecord true if the confirmation record is for use by Alice
	 * @return the confirmation record
	 */
	byte[] deriveConfirmationRecord(SecretKey sharedSecret,
			byte[] theirPayload, byte[] ourPayload,
			PublicKey theirPublicKey, KeyPair ourKeyPair,
			boolean alice, boolean aliceRecord);
}
