package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * The private half of a public/private {@link KeyPair}.
 */
@NotNullByDefault
public interface PrivateKey {

	/**
	 * Returns the encoded representation of this key.
	 */
	byte[] getEncoded();
}
