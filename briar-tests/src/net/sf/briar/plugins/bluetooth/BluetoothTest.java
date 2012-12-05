package net.sf.briar.plugins.bluetooth;

import java.util.UUID;

class BluetoothTest {

	private static final String EMPTY_UUID =
			UUID.nameUUIDFromBytes(new byte[0]).toString().replaceAll("-", "");

	static String getUuid() {
		return EMPTY_UUID;
	}
}