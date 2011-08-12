package net.sf.briar.crypto;

import java.util.Arrays;

import javax.crypto.spec.IvParameterSpec;

/**
 * An encrypted shared secret from which authentication and encryption keys can
 * be derived. The encrypted secret carries an IV for encrypting and decrypting
 * it and a flag indicating whether Alice's keys or Bob's keys should be
 * derived from the secret.
 * <p>
 * When two parties agree on a shared secret, they must determine which of them
 * will derive Alice's keys and which Bob's. Each party then encrypts the
 * secret with an independent key and IV.
 */
class SharedSecret {

	private static final int IV_BYTES = 16;

	private final IvParameterSpec iv;
	private final boolean alice;
	private final byte[] ciphertext;

	SharedSecret(byte[] secret) {
		if(secret.length < IV_BYTES + 2) throw new IllegalArgumentException();
		iv = new IvParameterSpec(secret, 0, IV_BYTES);
		switch(secret[IV_BYTES]) {
		case 0:
			alice = false;
			break;
		case 1:
			alice = true;
			break;
		default:
			throw new IllegalArgumentException();
		}
		ciphertext = Arrays.copyOfRange(secret, IV_BYTES + 1, secret.length);
	}

	SharedSecret(IvParameterSpec iv, boolean alice, byte[] ciphertext) {
		if(iv.getIV().length != IV_BYTES) throw new IllegalArgumentException();
		this.iv = iv;
		this.alice = alice;
		this.ciphertext = ciphertext;
	}

	/** Returns the IV used for encrypting and decrypting the secret. */
	IvParameterSpec getIv() {
		return iv;
	}

	/**
	 * Returns true if we should play the role of Alice in connections using
	 * this secret, or false if we should play the role of Bob.
	 */
	boolean getAlice() {
		return alice;
	}

	/** Returns the encrypted shared secret. */
	byte[] getCiphertext() {
		return ciphertext;
	}

	/**
	 * Returns a raw representation of the encrypted shared secret, suitable
	 * for storing in the database.
	 */
	byte[] getBytes() {
		byte[] b = new byte[IV_BYTES + 1 + ciphertext.length];
		byte[] ivBytes = iv.getIV();
		assert ivBytes.length == IV_BYTES;
		System.arraycopy(ivBytes, 0, b, 0, IV_BYTES);
		if(alice) b[IV_BYTES] = (byte) 1;
		System.arraycopy(ciphertext, 0, b, IV_BYTES + 1, ciphertext.length);
		return b;
	}
}
