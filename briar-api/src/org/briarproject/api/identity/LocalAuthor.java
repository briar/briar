package org.briarproject.api.identity;

/** A pseudonym for the local user. */
public class LocalAuthor extends Author {

	public enum Status {

		ADDING(0), ACTIVE(1), REMOVING(2);

		private final int value;

		Status(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static Status fromValue(int value) {
			for (Status s : values()) if (s.value == value) return s;
			throw new IllegalArgumentException();
		}
	}

	private final byte[] privateKey;
	private final long created;
	private final Status status;

	public LocalAuthor(AuthorId id, String name, byte[] publicKey,
			byte[] privateKey, long created, Status status) {
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
	public Status getStatus() {
		return status;
	}
}
