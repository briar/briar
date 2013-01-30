package net.sf.briar.api.db.event;

import net.sf.briar.api.messaging.TransportId;

/** An event that is broadcast when a transport is removed. */
public class TransportRemovedEvent extends DatabaseEvent {

	private final TransportId transportId;

	public TransportRemovedEvent(TransportId transportId) {
		this.transportId = transportId;
	}

	public TransportId getTransportId() {
		return transportId;
	}
}
