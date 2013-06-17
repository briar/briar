package net.sf.briar.api.crypto;

public interface MessageDigest {

	/** @see {@link java.security.MessageDigest#digest()} */
	byte[] digest();

	/** @see {@link java.security.MessageDigest#digest(byte[])} */
	byte[] digest(byte[] input);

	/** @see {@link java.security.MessageDigest#digest(byte[], int, int)} */
	int digest(byte[] buf, int offset, int len);

	/** @see {@link java.security.MessageDigest#getDigestLength()} */
	int getDigestLength();

	/** @see {@link java.security.MessageDigest#reset()} */
	void reset();

	/** @see {@link java.security.MessageDigest#update(byte)} */
	void update(byte input);

	/** @see {@link java.security.MessageDigest#update(byte[])} */
	void update(byte[] input);

	/** @see {@link java.security.MessageDigest#update(byte[], int, int)} */
	void update(byte[] input, int offset, int len);
}
