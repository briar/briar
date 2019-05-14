package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Arrays;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_AGREEMENT_PUBLIC_KEY_BYTES;

@Immutable
@NotNullByDefault
public class Identity {

	private final LocalAuthor localAuthor;
	@Nullable
	private final byte[] handshakePublicKey, handshakePrivateKey;
	private final long created;

	public Identity(LocalAuthor localAuthor,
			@Nullable byte[] handshakePublicKey,
			@Nullable byte[] handshakePrivateKey, long created) {
		if (handshakePublicKey != null) {
			int keyLength = handshakePublicKey.length;
			if (keyLength == 0 || keyLength > MAX_AGREEMENT_PUBLIC_KEY_BYTES)
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
	public byte[] getHandshakePublicKey() {
		return handshakePublicKey;
	}

	/**
	 * Returns the private key used for handshaking, or null if no key exists.
	 */
	@Nullable
	public byte[] getHandshakePrivateKey() {
		return handshakePrivateKey;
	}

	/**
	 * Returns the time the identity was created, in milliseconds since the
	 * Unix epoch.
	 */
	public long getTimeCreated() {
		return created;
	}

	@Override
	public int hashCode() {
		return localAuthor.getId().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Identity) {
			Identity i = (Identity) o;
			return created == i.created &&
					localAuthor.equals(i.localAuthor) &&
					Arrays.equals(handshakePublicKey, i.handshakePublicKey) &&
					Arrays.equals(handshakePrivateKey, i.handshakePrivateKey);
		}
		return false;
	}
}
