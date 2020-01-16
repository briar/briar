package org.briarproject.bramble.api.plugin;

public interface BluetoothConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.bluetooth");

	int UUID_BYTES = 16;

	String PROP_ADDRESS = "address";
	String PROP_UUID = "uuid";

	String PREF_BT_ENABLE = "enable";

	int REASON_USER = 1;
	int REASON_NO_BT_ADAPTER = 2;
}
