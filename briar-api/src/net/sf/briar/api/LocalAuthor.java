package net.sf.briar.api;

/** A pseudonym for the local user. */
public class LocalAuthor extends Author {

	private final byte[] privateKey;

	public LocalAuthor(AuthorId id, String name, byte[] publicKey,
			byte[] privateKey) {
		super(id, name, publicKey);
		this.privateKey = privateKey;
	}

	/**  Returns the private key used to generate the pseudonym's signatures. */
	public byte[] getPrivateKey() {
		return privateKey;
	}
}
