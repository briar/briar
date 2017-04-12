package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.UnsupportedEncodingException;

import javax.annotation.concurrent.Immutable;

/**
 * A pseudonym for a user.
 */
@Immutable
@NotNullByDefault
public class Author {

	public enum Status {
		NONE, ANONYMOUS, UNKNOWN, UNVERIFIED, VERIFIED, OURSELVES
	}

	private final AuthorId id;
	private final String name;
	private final byte[] publicKey;

	public Author(AuthorId id, String name, byte[] publicKey) {
		int length;
		try {
			length = name.getBytes("UTF-8").length;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		if (length == 0 || length > AuthorConstants.MAX_AUTHOR_NAME_LENGTH)
			throw new IllegalArgumentException();
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
	}

	/**
	 * Returns the author's unique identifier.
	 */
	public AuthorId getId() {
		return id;
	}

	/**
	 * Returns the author's name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the public key used to verify the pseudonym's signatures.
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
