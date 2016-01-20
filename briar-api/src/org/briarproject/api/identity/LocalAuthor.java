package org.briarproject.api.identity;

import org.briarproject.api.db.StorageStatus;

/** A pseudonym for the local user. */
public class LocalAuthor extends Author {

	private final byte[] privateKey;
	private final long created;
	private final StorageStatus status;

	public LocalAuthor(AuthorId id, String name, byte[] publicKey,
			byte[] privateKey, long created, StorageStatus status) {
		super(id, name, publicKey);
		this.privateKey = privateKey;
		this.created = created;
		this.status = status;
	}

	/**  Returns the private key used to generate the pseudonym's signatures. */
	public byte[] getPrivateKey() {
		return privateKey;
	}

	/**
	 * Returns the time the pseudonym was created, in milliseconds since the
	 * Unix epoch.
	 */
	public long getTimeCreated() {
		return created;
	}

	/** Returns the status of the pseudonym. */
	public StorageStatus getStatus() {
		return status;
	}
}
