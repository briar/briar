package org.briarproject.bramble.api.network;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class NetworkStatus {

	private final boolean connected, wifi, ipv6Only;

	public NetworkStatus(boolean connected, boolean wifi, boolean ipv6Only) {
		this.connected = connected;
		this.wifi = wifi;
		this.ipv6Only = ipv6Only;
	}

	public boolean isConnected() {
		return connected;
	}

	public boolean isWifi() {
		return wifi;
	}

	public boolean isIpv6Only() {
		return ipv6Only;
	}
}
