package net.sf.briar.api.protocol;

/** A pseudonymous author of {@link Message}s. */
public class Author {

	private final AuthorId id;
	private final String name;
	private final byte[] publicKey;

	public Author(AuthorId id, String name, byte[] publicKey) {
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
	}

	/** Returns the author's unique identifier. */
	public AuthorId getId() {
		return id;
	}

	/** Returns the author's name. */
	public String getName() {
		return name;
	}

	/**
	 * Returns the public key that is used to verify messages signed by the
	 * author.
	 */
	public byte[] getPublicKey() {
		return publicKey;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Author && id.equals(((Author) o).id);
	}
}
