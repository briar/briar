package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * A pseudonym for the local user.
 */
@Immutable
@NotNullByDefault
public class LocalAuthor extends Author {

	private final byte[] privateKey;

	public LocalAuthor(AuthorId id, int formatVersion, String name,
			byte[] publicKey, byte[] privateKey) {
		super(id, formatVersion, name, publicKey);
		this.privateKey = privateKey;
	}

	/**
	 * Returns the private key used to generate the pseudonym's signatures.
	 */
	public byte[] getPrivateKey() {
		return privateKey;
	}
}
