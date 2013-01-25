package net.sf.briar.api.db.event;

import net.sf.briar.api.protocol.TransportId;

/** An event that is broadcast when a transport is added. */
public class TransportAddedEvent extends DatabaseEvent {

	private final TransportId transportId;

	public TransportAddedEvent(TransportId transportId) {
		this.transportId = transportId;
	}

	public TransportId getTransportId() {
		return transportId;
	}
}
