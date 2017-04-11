package org.briarproject.bramble.api.plugin;

public interface TorConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.tor");

	int SOCKS_PORT = 59050;
	int CONTROL_PORT = 59051;

	int CONNECT_TO_PROXY_TIMEOUT = 5000; // Milliseconds
	int EXTRA_SOCKET_TIMEOUT = 30000; // Milliseconds

	String PREF_TOR_NETWORK = "network";
	String PREF_TOR_PORT = "port";

	int PREF_TOR_NETWORK_NEVER = 0;
	int PREF_TOR_NETWORK_WIFI = 1;
	int PREF_TOR_NETWORK_ALWAYS = 2;
}
