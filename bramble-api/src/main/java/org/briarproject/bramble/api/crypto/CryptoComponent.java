package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.annotation.Nullable;

@NotNullByDefault
public interface CryptoComponent {

	SecretKey generateSecretKey();

	SecureRandom getSecureRandom();

	KeyPair generateAgreementKeyPair();

	KeyParser getAgreementKeyParser();

	KeyPair generateSignatureKeyPair();

	KeyParser getSignatureKeyParser();

	KeyParser getMessageKeyParser();

	/**
	 * Derives another secret key from the given secret key.
	 *
	 * @param label A namespaced label indicating the purpose of the derived
	 * key, to prevent it from being repurposed or colliding with a key derived
	 * for another purpose
	 * @param inputs Additional inputs that will be included in the derivation
	 * of the key
	 */
	SecretKey deriveKey(String label, SecretKey k, byte[]... inputs);

	/**
	 * Derives a shared secret from two key pairs.
	 *
	 * @param label A namespaced label indicating the purpose of this shared
	 * secret, to prevent it from being repurposed or colliding with a shared
	 * secret derived for another purpose
	 * @param theirPublicKey The public key of the remote party
	 * @param ourKeyPair The key pair of the local party
	 * @param inputs Additional inputs that will be included in the derivation
	 * of the shared secret
	 * @return The shared secret
	 */
	SecretKey deriveSharedSecret(String label, PublicKey theirPublicKey,
			KeyPair ourKeyPair, byte[]... inputs)
			throws GeneralSecurityException;

	/**
	 * Derives a shared secret from two static and two ephemeral key pairs.
	 *
	 * @param label A namespaced label indicating the purpose of this shared
	 * secret, to prevent it from being repurposed or colliding with a shared
	 * secret derived for another purpose
	 * @param theirStaticPublicKey The static public key of the remote party
	 * @param theirEphemeralPublicKey The ephemeral public key of the remote
	 * party
	 * @param ourStaticKeyPair The static key pair of the local party
	 * @param ourEphemeralKeyPair The ephemeral key pair of the local party
	 * @param alice True if the local party is Alice
	 * @param inputs Additional inputs that will be included in the
	 * derivation of the shared secret
	 * @return The shared secret
	 */
	SecretKey deriveSharedSecret(String label, PublicKey theirStaticPublicKey,
			PublicKey theirEphemeralPublicKey, KeyPair ourStaticKeyPair,
			KeyPair ourEphemeralKeyPair, boolean alice, byte[]... inputs)
			throws GeneralSecurityException;

	/**
	 * Signs the given byte[] with the given private key.
	 *
	 * @param label A namespaced label indicating the purpose of this
	 * signature, to prevent it from being repurposed or colliding with a
	 * signature created for another purpose
	 */
	byte[] sign(String label, byte[] toSign, PrivateKey privateKey)
			throws GeneralSecurityException;

	/**
	 * Verifies that the given signature is valid for the signed data
	 * and the given public key.
	 *
	 * @param label A namespaced label indicating the purpose of this
	 * signature, to prevent it from being repurposed or colliding with a
	 * signature created for another purpose
	 * @return True if the signature was valid, false otherwise.
	 */
	boolean verifySignature(byte[] signature, String label, byte[] signed,
			PublicKey publicKey) throws GeneralSecurityException;

	/**
	 * Returns the hash of the given inputs. The inputs are unambiguously
	 * combined by prefixing each input with its length.
	 *
	 * @param label A namespaced label indicating the purpose of this hash, to
	 * prevent it from being repurposed or colliding with a hash created for
	 * another purpose
	 */
	byte[] hash(String label, byte[]... inputs);

	/**
	 * Returns a message authentication code with the given key over the
	 * given inputs. The inputs are unambiguously combined by prefixing each
	 * input with its length.
	 *
	 * @param label A namespaced label indicating the purpose of this MAC, to
	 * prevent it from being repurposed or colliding with a MAC created for
	 * another purpose
	 */
	byte[] mac(String label, SecretKey macKey, byte[]... inputs);

	/**
	 * Verifies that the given message authentication code is valid for the
	 * given secret key and inputs.
	 *
	 * @param label A namespaced label indicating the purpose of this MAC, to
	 * prevent it from being repurposed or colliding with a MAC created for
	 * another purpose
	 * @return True if the MAC was valid, false otherwise.
	 */
	boolean verifyMac(byte[] mac, String label, SecretKey macKey,
			byte[]... inputs);

	/**
	 * Encrypts and authenticates the given plaintext so it can be written to
	 * storage. The encryption and authentication keys are derived from the
	 * given password. The ciphertext will be decryptable using the same
	 * password after the app restarts.
	 *
	 * @param keyStrengthener Used to strengthen the password-based key. If
	 * null, the password-based key will not be strengthened
	 */
	byte[] encryptWithPassword(byte[] plaintext, String password,
			@Nullable KeyStrengthener keyStrengthener);

	/**
	 * Decrypts and authenticates the given ciphertext that has been read from
	 * storage. The encryption and authentication keys are derived from the
	 * given password.
	 *
	 * @param keyStrengthener Used to strengthen the password-based key. If
	 * null, or if strengthening was not used when encrypting the ciphertext,
	 * the password-based key will not be strengthened
	 * @throws DecryptionException If the ciphertext cannot be decrypted and
	 * authenticated (for example, if the password is wrong).
	 */
	byte[] decryptWithPassword(byte[] ciphertext, String password,
			@Nullable KeyStrengthener keyStrengthener)
			throws DecryptionException;

	/**
	 * Returns true if the given ciphertext was encrypted using a strengthened
	 * key. The validity of the ciphertext is not checked.
	 */
	boolean isEncryptedWithStrengthenedKey(byte[] ciphertext);

	/**
	 * Encrypts the given plaintext to the given public key.
	 */
	byte[] encryptToKey(PublicKey publicKey, byte[] plaintext);

	/**
	 * Encodes the given data as a hex string divided into lines of the given
	 * length. The line terminator is CRLF.
	 */
	String asciiArmour(byte[] b, int lineLength);

	/**
	 * Encode the onion/hidden service address given its public key. As
	 * specified here: https://gitweb.torproject.org/torspec.git/tree/rend-spec-v3.txt?id=29245fd5#n2135
	 */
	String encodeOnionAddress(byte[] publicKey);

}
