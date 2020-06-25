package org.briarproject.bramble.api.plugin;

public interface BluetoothConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.bluetooth");

	int UUID_BYTES = 16;

	// Transport properties
	String PROP_ADDRESS = "address";
	String PROP_UUID = "uuid";

	// Default value for PREF_PLUGIN_ENABLE
	boolean DEFAULT_PREF_PLUGIN_ENABLE = false;
}
