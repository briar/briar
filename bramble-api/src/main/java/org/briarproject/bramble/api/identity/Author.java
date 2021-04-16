package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.Nameable;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.crypto.CryptoConstants.KEY_TYPE_SIGNATURE;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.toUtf8;

/**
 * A pseudonym for a user.
 */
@Immutable
@NotNullByDefault
public class Author implements Nameable {

	/**
	 * The current version of the author structure.
	 */
	public static final int FORMAT_VERSION = 1;

	private final AuthorId id;
	private final int formatVersion;
	private final String name;
	private final PublicKey publicKey;

	public Author(AuthorId id, int formatVersion, String name,
			PublicKey publicKey) {
		int nameLength = toUtf8(name).length;
		if (nameLength == 0 || nameLength > MAX_AUTHOR_NAME_LENGTH)
			throw new IllegalArgumentException();
		if (!publicKey.getKeyType().equals(KEY_TYPE_SIGNATURE))
			throw new IllegalArgumentException();
		this.id = id;
		this.formatVersion = formatVersion;
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
	 * Returns the version of the author structure used to create the author.
	 */
	public int getFormatVersion() {
		return formatVersion;
	}

	/**
	 * Returns the author's name.
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Returns the public key used to verify the pseudonym's signatures.
	 */
	public PublicKey getPublicKey() {
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
