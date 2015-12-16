package org.briarproject.api.sync;

import org.briarproject.api.TransportId;

/** A packet acknowledging a {@link TransportUpdate}. */
public class TransportAck {

	private final TransportId id;
	private final long version;

	public TransportAck(TransportId id, long version) {
		this.id = id;
		this.version = version;
	}

	/** Returns the identifier of the updated transport. */
	public TransportId getId() {
		return id;
	}

	/** Returns the version number of the acknowledged update. */
	public long getVersion() {
		return version;
	}
}
