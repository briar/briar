package org.briarproject.api.event;

import org.briarproject.api.TransportId;

/** An event that is broadcast when a transport is added. */
public class TransportAddedEvent extends Event {

	private final TransportId transportId;
	private final long maxLatency;

	public TransportAddedEvent(TransportId transportId, long maxLatency) {
		this.transportId = transportId;
		this.maxLatency = maxLatency;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public long getMaxLatency() {
		return maxLatency;
	}
}
