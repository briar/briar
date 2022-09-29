package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when all sync connections using a given
 * transport should be closed.
 */
@Immutable
@NotNullByDefault
public class CloseSyncConnectionsEvent extends Event {

	private final TransportId transportId;

	public CloseSyncConnectionsEvent(TransportId transportId) {
		this.transportId = transportId;
	}

	public TransportId getTransportId() {
		return transportId;
	}
}
