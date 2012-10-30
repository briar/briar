package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;

public interface CryptoComponent {

	/**
	 * Derives a tag key from the given temporary secret.
	 * @param alice Indicates whether the key is for connections initiated by
	 * Alice or Bob.
	 */
	ErasableKey deriveTagKey(byte[] secret, boolean alice);

	/**
	 * Derives a frame key from the given temporary secret and connection
	 * number.
	 * @param alice Indicates whether the key is for a connection initiated by
	 * Alice or Bob.
	 * @param initiator Indicates whether the key is for the initiator's or the
	 * responder's side of the connection.
	 */
	ErasableKey deriveFrameKey(byte[] secret, long connection, boolean alice,
			boolean initiator);

	/**
	 * Derives an initial shared secret from two public keys and one of the
	 * corresponding private keys.
	 * @param alice Indicates whether the private key belongs to Alice or Bob.
	 */
	byte[] deriveInitialSecret(byte[] ourPublicKey, byte[] theirPublicKey,
			PrivateKey ourPrivateKey, boolean alice);

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

	KeyPair generateSignatureKeyPair();

	KeyParser getSignatureKeyParser();

	ErasableKey generateTestKey();

	MessageDigest getMessageDigest();

	PseudoRandom getPseudoRandom(int seed);

	SecureRandom getSecureRandom();

	Cipher getTagCipher();

	AuthenticatedCipher getFrameCipher();

	Signature getSignature();
}
