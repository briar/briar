package org.briarproject.bramble.api.plugin;

import static java.util.concurrent.TimeUnit.SECONDS;

public interface TorConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.tor");

	// Transport properties
	String PROP_ONION_V3 = "onion3";

	int DEFAULT_SOCKS_PORT = 59050;
	int DEFAULT_CONTROL_PORT = 59051;

	int CONNECT_TO_PROXY_TIMEOUT = (int) SECONDS.toMillis(5);
	int EXTRA_CONNECT_TIMEOUT = (int) SECONDS.toMillis(120);
	int EXTRA_SOCKET_TIMEOUT = (int) SECONDS.toMillis(30);

	// Local settings (not shared with contacts)
	String PREF_TOR_NETWORK = "network2";
	String PREF_TOR_PORT = "port";
	String PREF_TOR_MOBILE = "useMobileData";
	String PREF_TOR_ONLY_WHEN_CHARGING = "onlyWhenCharging";
	String HS_PRIVATE_KEY_V3 = "onionPrivKey3";

	// Values for PREF_TOR_NETWORK
	int PREF_TOR_NETWORK_AUTOMATIC = 0;
	int PREF_TOR_NETWORK_WITHOUT_BRIDGES = 1;
	int PREF_TOR_NETWORK_WITH_BRIDGES = 2;

	// Default values for local settings
	boolean DEFAULT_PREF_PLUGIN_ENABLE = true;
	int DEFAULT_PREF_TOR_NETWORK = PREF_TOR_NETWORK_AUTOMATIC;
	boolean DEFAULT_PREF_TOR_MOBILE = true;
	boolean DEFAULT_PREF_TOR_ONLY_WHEN_CHARGING = false;

	/**
	 * Reason flag returned by {@link Plugin#getReasonsDisabled()}.
	 */
	int REASON_BATTERY = 2;

	/**
	 * Reason flag returned by {@link Plugin#getReasonsDisabled()}.
	 */
	int REASON_MOBILE_DATA = 4;

	/**
	 * Reason flag returned by {@link Plugin#getReasonsDisabled()}.
	 */
	int REASON_COUNTRY_BLOCKED = 8;
}
