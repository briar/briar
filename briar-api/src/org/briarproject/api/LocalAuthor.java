package org.briarproject.api;

/** A pseudonym for the local user. */
public class LocalAuthor extends Author {

	private final byte[] privateKey;
	private final long created;

	public LocalAuthor(AuthorId id, String name, byte[] publicKey,
			byte[] privateKey, long created) {
		super(id, name, publicKey);
		this.privateKey = privateKey;
		this.created = created;
	}

	/**  Returns the private key used to generate the pseudonym's signatures. */
	public byte[] getPrivateKey() {
		return privateKey;
	}

	public long getTimeCreated() {
		return created;
	}
}
