package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.crypto.CryptoConstants.KEY_TYPE_AGREEMENT;

@Immutable
@NotNullByDefault
public class Identity {

	private final LocalAuthor localAuthor;
	@Nullable
	private final PublicKey handshakePublicKey;
	@Nullable
	private final PrivateKey handshakePrivateKey;
	private final long created;

	public Identity(LocalAuthor localAuthor,
			@Nullable PublicKey handshakePublicKey,
			@Nullable PrivateKey handshakePrivateKey, long created) {
		if (handshakePublicKey != null) {
			if (handshakePrivateKey == null)
				throw new IllegalArgumentException();
			if (!handshakePublicKey.getKeyType().equals(KEY_TYPE_AGREEMENT))
				throw new IllegalArgumentException();
		}
		if (handshakePrivateKey != null) {
			if (handshakePublicKey == null)
				throw new IllegalArgumentException();
			if (!handshakePrivateKey.getKeyType().equals(KEY_TYPE_AGREEMENT))
				throw new IllegalArgumentException();
		}
		this.localAuthor = localAuthor;
		this.handshakePublicKey = handshakePublicKey;
		this.handshakePrivateKey = handshakePrivateKey;
		this.created = created;
	}

	/**
	 * Returns the ID of the user's pseudonym.
	 */
	public AuthorId getId() {
		return localAuthor.getId();
	}

	/**
	 * Returns the user's pseudonym.
	 */
	public LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	/**
	 * Returns true if the identity has a handshake key pair.
	 */
	public boolean hasHandshakeKeyPair() {
		return handshakePublicKey != null && handshakePrivateKey != null;
	}

	/**
	 * Returns the public key used for handshaking, or null if no key exists.
	 */
	@Nullable
	public PublicKey getHandshakePublicKey() {
		return handshakePublicKey;
	}

	/**
	 * Returns the private key used for handshaking, or null if no key exists.
	 */
	@Nullable
	public PrivateKey getHandshakePrivateKey() {
		return handshakePrivateKey;
	}

	/**
	 * Returns the time the identity was created, in milliseconds since the
	 * Unix epoch.
	 */
	public long getTimeCreated() {
		return created;
	}
}
