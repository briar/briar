package net.sf.briar.api.protocol;

/**
 * A packet updating the recipient's view of the retention time of the sender's
 * database.
 */
public class RetentionUpdate {

	private final long retention, version;

	public RetentionUpdate(long retention, long version) {
		this.retention = retention;
		this.version = version;
	}

	public long getRetentionTime() {
		return retention;
	}

	public long getVersion() {
		return version;
	}
}
