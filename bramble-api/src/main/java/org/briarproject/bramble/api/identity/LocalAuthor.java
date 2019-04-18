package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;

/**
 * A pseudonym for the local user.
 */
@Immutable
@NotNullByDefault
public class LocalAuthor extends Author {

	private final byte[] privateKey;
	@Nullable
	private final byte[] handshakePublicKey, handshakePrivateKey;
	private final long created;

	public LocalAuthor(AuthorId id, int formatVersion, String name,
			byte[] publicKey, byte[] privateKey, long created) {
		super(id, formatVersion, name, publicKey);
		this.privateKey = privateKey;
		this.created = created;
		handshakePublicKey = null;
		handshakePrivateKey = null;
	}

	public LocalAuthor(AuthorId id, int formatVersion, String name,
			byte[] publicKey, byte[] privateKey, byte[] handshakePublicKey,
			byte[] handshakePrivateKey, long created) {
		super(id, formatVersion, name, publicKey);
		if (handshakePublicKey.length == 0 ||
				handshakePublicKey.length > MAX_PUBLIC_KEY_LENGTH) {
			throw new IllegalArgumentException();
		}
		this.privateKey = privateKey;
		this.handshakePublicKey = handshakePublicKey;
		this.handshakePrivateKey = handshakePrivateKey;
		this.created = created;
	}

	/**
	 * Returns the private key used to generate the pseudonym's signatures.
	 */
	public byte[] getPrivateKey() {
		return privateKey;
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
	 * Returns the time the pseudonym was created, in milliseconds since the
	 * Unix epoch.
	 */
	public long getTimeCreated() {
		return created;
	}
}
