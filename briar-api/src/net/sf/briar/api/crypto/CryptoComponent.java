package net.sf.briar.api.crypto;

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
	 * Derives two confirmation codes from the given master secret. The first
	 * code is for Alice to give to Bob; the second is for Bob to give to
	 * Alice.
	 */
	int[] deriveConfirmationCodes(byte[] secret);

	/**
	 * Derives two nonces from the given master secret. The first nonce is for
	 * Alice to sign; the second is for Bob to sign.
	 */
	byte[][] deriveInvitationNonces(byte[] secret);

	/**
	 * Derives a shared master secret from two public keys and one of the
	 * corresponding private keys.
	 * @param alice indicates whether the private key belongs to Alice or Bob.
	 */
	byte[] deriveMasterSecret(byte[] theirPublicKey, KeyPair ourKeyPair,
			boolean alice) throws GeneralSecurityException;

	/**
	 * Derives an initial secret for the given transport from the given master
	 * secret.
	 */
	byte[] deriveInitialSecret(byte[] secret, int transportIndex);

	/**
	 * Derives a temporary secret for the given period from the given secret,
	 * which is either the initial shared secret or the previous period's
	 * temporary secret.
	 */
	byte[] deriveNextSecret(byte[] secret, long period);

	/**
	 * Derives a tag key from the given temporary secret.
	 * @param alice indicates whether the key is for connections initiated by
	 * Alice or Bob.
	 */
	SecretKey deriveTagKey(byte[] secret, boolean alice);

	/**
	 * Derives a frame key from the given temporary secret and connection
	 * number.
	 * @param alice indicates whether the key is for a connection initiated by
	 * Alice or Bob.
	 * @param initiator indicates whether the key is for the initiator's or the
	 * responder's side of the connection.
	 */
	SecretKey deriveFrameKey(byte[] secret, long connection, boolean alice,
			boolean initiator);

	/** Returns a cipher for encrypting and authenticating connections. */
	AuthenticatedCipher getFrameCipher();

	/** Encodes the pseudo-random tag that is used to recognise a connection. */
	void encodeTag(byte[] tag, SecretKey tagKey, long connection);

	/**
	 * Encrypts and authenticates the given plaintext so it can be written to
	 * storage. The encryption and authentication keys are derived from the
	 * given password. The ciphertext will be decryptable using the same
	 * password after the app restarts.
	 */
	byte[] encryptWithPassword(byte[] plaintext, char[] password);

	/**
	 * Decrypts and authenticates the given ciphertext that has been read from
	 * storage. The encryption and authentication keys are derived from the
	 * given password. Returns null if the ciphertext cannot be decrypted and
	 * authenticated (for example, if the password is wrong).
	 */
	byte[] decryptWithPassword(byte[] ciphertext, char[] password);
}
