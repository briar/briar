package org.briarproject.bramble.api.plugin;

public interface BluetoothConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.bluetooth");

	int UUID_BYTES = 16;

	String PROP_ADDRESS = "address";
	String PROP_UUID = "uuid";

	// Reason code returned by Plugin#getReasonDisabled()
	int REASON_NO_BT_ADAPTER = 2;
}
