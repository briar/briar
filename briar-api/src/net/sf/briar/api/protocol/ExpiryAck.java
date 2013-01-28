package net.sf.briar.api.protocol;

/** A packet acknowledging a (@link ExpiryUpdate} */
public class ExpiryAck {

	private final long version;

	public ExpiryAck(long version) {
		this.version = version;
	}

	public long getVersionNumber() {
		return version;
	}
}
