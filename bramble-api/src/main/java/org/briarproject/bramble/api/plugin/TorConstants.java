package org.briarproject.bramble.api.plugin;

import static java.util.concurrent.TimeUnit.DAYS;

public interface TorConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.tor");

	// Transport properties
	String PROP_ONION_V2 = "onion";
	String PROP_ONION_V3 = "onion3";

	int DEFAULT_SOCKS_PORT = 59050;
	int DEFAULT_CONTROL_PORT = 59051;

	int CONNECT_TO_PROXY_TIMEOUT = 5000; // Milliseconds
	int EXTRA_SOCKET_TIMEOUT = 30000; // Milliseconds

	// Local settings (not shared with contacts)
	String PREF_TOR_NETWORK = "network2";
	String PREF_TOR_PORT = "port";
	String PREF_TOR_MOBILE = "useMobileData";
	String PREF_TOR_ONLY_WHEN_CHARGING = "onlyWhenCharging";
	String HS_PRIVATE_KEY_V2 = "onionPrivKey";
	String HS_PRIVATE_KEY_V3 = "onionPrivKey3";
	String HS_V3_CREATED = "onionPrivKey3Created";

	/**
	 * How long to publish a v3 hidden service before retiring the v2 service.
	 */
	long V3_MIGRATION_PERIOD_MS = DAYS.toMillis(180);

	// Values for PREF_TOR_NETWORK
	int PREF_TOR_NETWORK_AUTOMATIC = 0;
	int PREF_TOR_NETWORK_WITHOUT_BRIDGES = 1;
	int PREF_TOR_NETWORK_WITH_BRIDGES = 2;
	// TODO: Remove when settings migration code is removed
	int PREF_TOR_NETWORK_NEVER = 3;

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
