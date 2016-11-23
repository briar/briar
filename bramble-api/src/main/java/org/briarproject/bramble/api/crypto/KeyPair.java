package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * A key pair consisting of a {@link PublicKey} and a {@link PrivateKey).
 */
@Immutable
@NotNullByDefault
public class KeyPair {

	private final PublicKey publicKey;
	private final PrivateKey privateKey;

	public KeyPair(PublicKey publicKey, PrivateKey privateKey) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
	}

	public PublicKey getPublic() {
		return publicKey;
	}

	public PrivateKey getPrivate() {
		return privateKey;
	}
}
