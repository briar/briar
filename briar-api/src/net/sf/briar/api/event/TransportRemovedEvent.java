package net.sf.briar.api.event;

import net.sf.briar.api.TransportId;

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
