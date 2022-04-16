package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a plugin's polling interval decreases.
 */
@Immutable
@NotNullByDefault
public class PollingIntervalDecreasedEvent extends Event {

	private final TransportId transportId;

	public PollingIntervalDecreasedEvent(TransportId transportId) {
		this.transportId = transportId;
	}

	public TransportId getTransportId() {
		return transportId;
	}
}
