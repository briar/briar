package net.sf.briar.api.crypto;

/** A secret key used for encryption and/or authentication. */
public interface SecretKey {

	/** Returns the encoded representation of this key. */
	byte[] getEncoded();

	/**
	 * Returns a copy of this key - erasing this key will erase the copy and
	 * vice versa.
	 */
	SecretKey copy();

	/**
	 * Erases this key from memory. Any copies derived from this key via the
	 * {@link #copy()} method, and any keys from which this key was derived via
	 * the {@link #copy()} method, are also erased.
	 */
	void erase();
}
