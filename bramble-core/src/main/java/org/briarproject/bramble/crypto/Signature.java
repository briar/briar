package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

@NotNullByDefault
interface Signature {

	/**
	 * @see {@link java.security.Signature#initSign(java.security.PrivateKey)}
	 */
	void initSign(PrivateKey k) throws GeneralSecurityException;

	/**
	 * @see {@link java.security.Signature#initVerify(java.security.PublicKey)}
	 */
	void initVerify(PublicKey k) throws GeneralSecurityException;

	/**
	 * @see {@link java.security.Signature#update(byte)}
	 */
	void update(byte b) throws GeneralSecurityException;

	/**
	 * @see {@link java.security.Signature#update(byte[])}
	 */
	void update(byte[] b) throws GeneralSecurityException;

	/**
	 * @see {@link java.security.Signature#update(byte[], int, int)}
	 */
	void update(byte[] b, int off, int len) throws GeneralSecurityException;

	/**
	 * @see {@link java.security.Signature#sign()}
	 */
	byte[] sign() throws GeneralSecurityException;

	/**
	 * @see {@link java.security.Signature#verify(byte[])}
	 */
	boolean verify(byte[] signature) throws GeneralSecurityException;
}
