package org.briarproject.bramble.api.plugin;

public interface BluetoothConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.bluetooth");

	int UUID_BYTES = 16;

	// Transport properties
	String PROP_ADDRESS = "address";
	String PROP_UUID = "uuid";

	// Local settings (not shared with contacts)
	String PREF_ADDRESS_IS_REFLECTED = "addressIsReflected";

	// Default value for PREF_PLUGIN_ENABLE
	boolean DEFAULT_PREF_PLUGIN_ENABLE = false;
}
