package net.sf.briar.api.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;

public interface CryptoComponent {

	/**
	 * Derives a tag key from the given temporary secret.
	 * @param alice indicates whether the key is for connections initiated by
	 * Alice or Bob.
	 */
	ErasableKey deriveTagKey(byte[] secret, boolean alice);

	/**
	 * Derives a frame key from the given temporary secret and connection
	 * number.
	 * @param alice indicates whether the key is for a connection initiated by
	 * Alice or Bob.
	 * @param initiator indicates whether the key is for the initiator's or the
	 * responder's side of the connection.
	 */
	ErasableKey deriveFrameKey(byte[] secret, long connection, boolean alice,
			boolean initiator);

	/**
	 * Derives an initial shared secret from two public keys and one of the
	 * corresponding private keys.
	 * @param alice indicates whether the private key belongs to Alice or Bob.
	 */
	byte[] deriveInitialSecret(byte[] theirPublicKey, KeyPair ourKeyPair,
			boolean alice) throws GeneralSecurityException;

	/**
	 * Generates a random invitation code.
	 */
	int generateInvitationCode();

	/**
	 * Derives two confirmation codes from the given initial shared secret. The
	 * first code is for Alice to give to Bob; the second is for Bob to give to
	 * Alice.
	 */
	int[] deriveConfirmationCodes(byte[] secret);

	/**
	 * Derives a temporary secret for the given period from the previous
	 * period's temporary secret.
	 */
	byte[] deriveNextSecret(byte[] secret, long period);

	/** Encodes the pseudo-random tag that is used to recognise a connection. */
	void encodeTag(byte[] tag, Cipher tagCipher, ErasableKey tagKey,
			long connection);

	KeyPair generateAgreementKeyPair();

	KeyParser getAgreementKeyParser();

	KeyPair generateSignatureKeyPair();

	KeyParser getSignatureKeyParser();

	ErasableKey generateSecretKey();

	MessageDigest getMessageDigest();

	PseudoRandom getPseudoRandom(int seed1, int seed2);

	SecureRandom getSecureRandom();

	Cipher getTagCipher();

	AuthenticatedCipher getFrameCipher();

	Signature getSignature();

	/**
	 * Encrypts the given plaintext so it can be written to temporary storage.
	 * The ciphertext will not be decryptable after the app restarts.
	 */
	byte[] encryptTemporaryStorage(byte[] plaintext);

	/**
	 * Decrypts the given ciphertext that has been read from temporary storage.
	 * Returns null if the ciphertext is not decryptable (for example, if it
	 * was written before the app restarted).
	 */
	byte[] decryptTemporaryStorage(byte[] ciphertext);
}
