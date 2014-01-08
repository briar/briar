package org.briarproject.api.messaging;

/** A packet acknowledging a (@link RetentionUpdate} */
public class RetentionAck {

	private final long version;

	public RetentionAck(long version) {
		this.version = version;
	}

	/** Returns the version number of the acknowledged update. */
	public long getVersion() {
		return version;
	}
}
