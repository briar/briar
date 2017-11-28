package org.briarproject.bramble.api.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public interface CryptoComponent {

	SecretKey generateSecretKey();

	SecureRandom getSecureRandom();

	KeyPair generateAgreementKeyPair();

	KeyParser getAgreementKeyParser();

	KeyPair generateSignatureKeyPair();

	KeyParser getSignatureKeyParser();

	KeyPair generateEdKeyPair();

	KeyParser getEdKeyParser();

	KeyParser getMessageKeyParser();

	/**
	 * Derives another secret key from the given secret key.
	 *
	 * @param label a namespaced label indicating the purpose of the derived
	 * key, to prevent it from being repurposed or colliding with a key derived
	 * for another purpose
	 */
	SecretKey deriveKey(String label, SecretKey k, byte[]... inputs);

	/**
	 * Derives a common shared secret from two public keys and one of the
	 * corresponding private keys.
	 *
	 * @param label a namespaced label indicating the purpose of this shared
	 * secret, to prevent it from being repurposed or colliding with a shared
	 * secret derived for another purpose
	 * @param theirPublicKey the public key of the remote party
	 * @param ourKeyPair the key pair of the local party
	 * @return the shared secret
	 */
	SecretKey deriveSharedSecret(String label, PublicKey theirPublicKey,
			KeyPair ourKeyPair, byte[]... inputs)
			throws GeneralSecurityException;

	/**
	 * Signs the given byte[] with the given ECDSA private key.
	 *
	 * @param label a namespaced label indicating the purpose of this
	 * signature, to prevent it from being repurposed or colliding with a
	 * signature created for another purpose
	 */
	byte[] sign(String label, byte[] toSign, byte[] privateKey)
			throws GeneralSecurityException;

	/**
	 * Signs the given byte[] with the given Ed25519 private key.
	 *
	 * @param label A label specific to this signature
	 *              to ensure that the signature cannot be repurposed
	 */
	byte[] signEd(String label, byte[] toSign, byte[] privateKey)
			throws GeneralSecurityException;

	/**
	 * Verifies that the given signature is valid for the signed data
	 * and the given ECDSA public key.
	 *
	 * @param label a namespaced label indicating the purpose of this
	 * signature, to prevent it from being repurposed or colliding with a
	 * signature created for another purpose
	 * @return true if the signature was valid, false otherwise.
	 */
	boolean verify(String label, byte[] signedData, byte[] publicKey,
			byte[] signature) throws GeneralSecurityException;

	/**
	 * Verifies that the given signature is valid for the signed data
	 * and the given Ed25519 public key.
	 *
	 * @param label A label that was specific to this signature
	 *              to ensure that the signature cannot be repurposed
	 * @return true if the signature was valid, false otherwise.
	 */
	boolean verifyEd(String label, byte[] signedData, byte[] publicKey,
			byte[] signature) throws GeneralSecurityException;

	/**
	 * Returns the hash of the given inputs. The inputs are unambiguously
	 * combined by prefixing each input with its length.
	 *
	 * @param label a namespaced label indicating the purpose of this hash, to
	 * prevent it from being repurposed or colliding with a hash created for
	 * another purpose
	 */
	byte[] hash(String label, byte[]... inputs);

	/**
	 * Returns a message authentication code with the given key over the
	 * given inputs. The inputs are unambiguously combined by prefixing each
	 * input with its length.
	 *
	 * @param label a namespaced label indicating the purpose of this MAC, to
	 * prevent it from being repurposed or colliding with a MAC created for
	 * another purpose
	 */
	byte[] mac(String label, SecretKey macKey, byte[]... inputs);

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

	/**
	 * Encrypts the given plaintext to the given public key.
	 */
	byte[] encryptToKey(PublicKey publicKey, byte[] plaintext);

	/**
	 * Encodes the given data as a hex string divided into lines of the given
	 * length. The line terminator is CRLF.
	 */
	String asciiArmour(byte[] b, int lineLength);
}
