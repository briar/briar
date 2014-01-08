package org.briarproject.api.event;

import org.briarproject.api.TransportId;

/** An event that is broadcast when a transport is removed. */
public class TransportRemovedEvent extends Event {

	private final TransportId transportId;

	public TransportRemovedEvent(TransportId transportId) {
		this.transportId = transportId;
	}

	public TransportId getTransportId() {
		return transportId;
	}
}
