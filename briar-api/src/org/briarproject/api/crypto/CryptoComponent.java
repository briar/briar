package org.briarproject.api.crypto;

import org.briarproject.api.TransportId;
import org.briarproject.api.transport.TransportKeys;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public interface CryptoComponent {

	SecretKey generateSecretKey();

	MessageDigest getMessageDigest();

	PseudoRandom getPseudoRandom(int seed1, int seed2);

	SecureRandom getSecureRandom();

	Signature getSignature();

	KeyPair generateAgreementKeyPair();

	KeyParser getAgreementKeyParser();

	KeyPair generateSignatureKeyPair();

	KeyParser getSignatureKeyParser();

	/** Generates a random invitation code. */
	int generateInvitationCode();

	/**
	 * Derives a shared master secret from two public keys and one of the
	 * corresponding private keys.
	 * @param alice whether the private key belongs to Alice or Bob.
	 */
	SecretKey deriveMasterSecret(byte[] theirPublicKey, KeyPair ourKeyPair,
			boolean alice) throws GeneralSecurityException;

	/**
	 * Derives a confirmation code from the given master secret.
	 * @param alice whether the code is for use by Alice or Bob.
	 */
	int deriveConfirmationCode(SecretKey master, boolean alice);

	/**
	 * Derives a header key for an invitation stream from the given master
	 * secret.
	 * @param alice whether the key is for use by Alice or Bob.
	 */
	SecretKey deriveInvitationKey(SecretKey master, boolean alice);

	/**
	 * Derives a nonce from the given master secret for one of the parties to
	 * sign.
	 * @param alice whether the nonce is for use by Alice or Bob.
	 */
	byte[] deriveSignatureNonce(SecretKey master, boolean alice);

	/** Derives a group salt from the given master secret. */
	byte[] deriveGroupSalt(SecretKey master);

	/**
	 * Derives initial transport keys for the given transport in the given
	 * rotation period from the given master secret.
	 * @param alice whether the keys are for use by Alice or Bob.
	 */
	TransportKeys deriveTransportKeys(TransportId t, SecretKey master,
			long rotationPeriod, boolean alice);

	/**
	 * Rotates the given transport keys to the given rotation period. If the
	 * keys are for a future rotation period they are not rotated.
	 */
	TransportKeys rotateTransportKeys(TransportKeys k, long rotationPeriod);

	/** Encodes the pseudo-random tag that is used to recognise a stream. */
	void encodeTag(byte[] tag, SecretKey tagKey, long streamNumber);

	/**
	 * Encrypts and authenticates the given plaintext so it can be written to
	 * storage. The encryption and authentication keys are derived from the
	 * given password. The ciphertext will be decryptable using the same
	 * password after the app restarts.
	 */
	byte[] encryptWithPassword(byte[] plaintext, String password);

	/**
	 * Decrypts and authenticates the given ciphertext that has been read from
	 * storage. The encryption and authentication keys are derived from the
	 * given password. Returns null if the ciphertext cannot be decrypted and
	 * authenticated (for example, if the password is wrong).
	 */
	byte[] decryptWithPassword(byte[] ciphertext, String password);
}
