package org.briarproject.api.keyagreement;

import org.briarproject.api.TransportId;
import org.briarproject.api.properties.TransportProperties;

/**
 * Describes how to connect to a device over a short-range transport.
 */
public class TransportDescriptor {

	private final TransportId id;
	private final TransportProperties properties;

	public TransportDescriptor(TransportId id, TransportProperties properties) {
		this.id = id;
		this.properties = properties;
	}

	/** Returns the transport identifier. */
	public TransportId getIdentifier() {
		return id;
	}

	/** Returns the transport properties. */
	public TransportProperties getProperties() {
		return properties;
	}
}
