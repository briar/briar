package org.briarproject.bramble.api.network;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class NetworkStatus {

	private final boolean connected, wifi;

	public NetworkStatus(boolean connected, boolean wifi) {
		this.connected = connected;
		this.wifi = wifi;
	}

	public boolean isConnected() {
		return connected;
	}

	public boolean isWifi() {
		return wifi;
	}
}
