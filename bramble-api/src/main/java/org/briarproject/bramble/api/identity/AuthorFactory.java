package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface AuthorFactory {

	/**
	 * Creates an author with the current format version and the given name and
	 * public key.
	 */
	Author createAuthor(String name, byte[] publicKey);

	/**
	 * Creates an author with the given format version, name and public key.
	 */
	Author createAuthor(int formatVersion, String name, byte[] publicKey);

	/**
	 * Creates a local author with the current format version and the given
	 * name.
	 *
	 * @param handshakeKeys true if the local author should include handshake
	 * keys.
	 */
	LocalAuthor createLocalAuthor(String name, boolean handshakeKeys);
}
