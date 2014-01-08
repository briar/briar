package org.briarproject.api.crypto;

/** A key pair consisting of a {@link PublicKey} and a {@link PrivateKey). */
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
