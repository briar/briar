package org.briarproject.api.event;

import org.briarproject.api.TransportId;

/** An event that is broadcast when a transport is disabled. */
public class TransportDisabledEvent extends Event {

	private final TransportId transportId;

	public TransportDisabledEvent(TransportId transportId) {
		this.transportId = transportId;
	}

	public TransportId getTransportId() {
		return transportId;
	}
}
