package net.sf.briar.api.messaging;

/** A pseudonym that the user can use to sign {@link Message}s. */
public class LocalAuthor extends Author {

	private final byte[] privateKey;

	public LocalAuthor(AuthorId id, String name, byte[] publicKey,
			byte[] privateKey) {
		super(id, name, publicKey);
		this.privateKey = privateKey;
	}

	/** Returns the private key that is used to sign messages. */
	public byte[] getPrivateKey() {
		return privateKey;
	}
}
