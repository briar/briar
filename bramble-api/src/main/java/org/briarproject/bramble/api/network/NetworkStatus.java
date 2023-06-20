package org.briarproject.bramble.api.network;

import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
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

	@Override
	public int hashCode() {
		return (connected ? 1 : 0) | (wifi ? 2 : 0) | (ipv6Only ? 4 : 0);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (o instanceof NetworkStatus) {
			NetworkStatus s = (NetworkStatus) o;
			return connected == s.connected
					&& wifi == s.wifi
					&& ipv6Only == s.ipv6Only;
		}
		return false;
	}
}
