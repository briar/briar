package org.briarproject.api.event;

import org.briarproject.api.TransportId;

/** An event that is broadcast when a transport is enabled. */
public class TransportEnabledEvent extends Event {

	private final TransportId transportId;

	public TransportEnabledEvent(TransportId transportId) {
		this.transportId = transportId;
	}

	public TransportId getTransportId() {
		return transportId;
	}
}
