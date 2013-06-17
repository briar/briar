package net.sf.briar.api.crypto;

import java.security.GeneralSecurityException;

public interface Signature {

	/**
	 * @see {@link java.security.Signature#initSign(java.security.PrivateKey)}
	 */
	void initSign(PrivateKey k) throws GeneralSecurityException;

	/**
	 * @see {@link java.security.Signature#initVafiry(java.security.PublicKey)}
	 */
	void initVerify(PublicKey k) throws GeneralSecurityException;

	/** @see {@link java.security.Signature#update(byte)} */
	void update(byte b);

	/** @see {@link java.security.Signature#update(byte[])} */
	void update(byte[] b);

	/** @see {@link java.security.Signature#update(byte[], int, int)} */
	void update(byte[] b, int off, int len);

	/** @see {@link java.security.Signature#sign()} */
	byte[] sign();

	/** @see {@link java.security.Signature#verify(byte[])} */
	boolean verify(byte[] signature);
}
