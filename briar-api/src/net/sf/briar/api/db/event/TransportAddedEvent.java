package net.sf.briar.api.db.event;

import net.sf.briar.api.TransportId;

/** An event that is broadcast when a transport is added to the database. */
public class TransportAddedEvent extends DatabaseEvent {

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
