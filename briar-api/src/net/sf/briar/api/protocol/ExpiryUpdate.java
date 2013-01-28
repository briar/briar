package net.sf.briar.api.protocol;

/**
 * A packet updating the recipient's view of the expiry time of the sender's
 * database.
 */
public class ExpiryUpdate {

	private final long expiry, version;

	public ExpiryUpdate(long expiry, long version) {
		this.expiry = expiry;
		this.version = version;
	}

	public long getExpiryTime() {
		return expiry;
	}

	public long getVersionNumber() {
		return version;
	}
}
