package net.sf.briar.api.crypto;

/**
 * A wrapper around a {@link java.security.MessageDigest} that allows it to be
 * replaced for testing.
 */
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
