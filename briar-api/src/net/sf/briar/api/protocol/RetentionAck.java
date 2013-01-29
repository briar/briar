package net.sf.briar.api.protocol;

/** A packet acknowledging a (@link RetentionUpdate} */
public class RetentionAck {

	private final long version;

	public RetentionAck(long version) {
		this.version = version;
	}

	public long getVersionNumber() {
		return version;
	}
}
