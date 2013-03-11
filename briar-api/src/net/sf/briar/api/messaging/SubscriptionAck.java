package net.sf.briar.api.messaging;

/** A packet acknowledging a {@link SubscriptionUpdate}. */
public class SubscriptionAck {

	private final long version;

	public SubscriptionAck(long version) {
		this.version = version;
	}

	/** Returns the version number of the acknowledged update. */
	public long getVersion() {
		return version;
	}
}
