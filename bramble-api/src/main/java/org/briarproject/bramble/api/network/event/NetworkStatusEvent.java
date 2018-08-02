package org.briarproject.bramble.api.network.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.network.NetworkStatus;

public class NetworkStatusEvent extends Event {

	private final NetworkStatus status;

	public NetworkStatusEvent(NetworkStatus status) {
		this.status = status;
	}

	public NetworkStatus getStatus() {
		return status;
	}
}