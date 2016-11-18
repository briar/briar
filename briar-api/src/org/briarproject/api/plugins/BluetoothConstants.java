package org.briarproject.api.plugins;

import org.briarproject.api.TransportId;

public interface BluetoothConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.bluetooth");

	int UUID_BYTES = 16;

	String PROP_ADDRESS = "address";
	String PROP_UUID = "uuid";

}
