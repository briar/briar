package net.sf.briar.api.messaging;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;

/**
 * A packet updating the recipient's view of the sender's transport properties.
 */
public class TransportUpdate {

	private final TransportId id;
	private final TransportProperties properties;
	private final long version;

	public TransportUpdate(TransportId id, TransportProperties properties,
			long version) {
		this.id = id;
		this.properties = properties;
		this.version = version;
	}

	/** Returns the identifier of the updated transport. */
	public TransportId getId() {
		return id;
	}

	/** Returns the transport's updated properties. */
	public TransportProperties getProperties() {
		return properties;
	}

	/** Returns the update's version number. */
	public long getVersion() {
		return version;
	}
}
