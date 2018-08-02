package org.briarproject.bramble.api.network;

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
