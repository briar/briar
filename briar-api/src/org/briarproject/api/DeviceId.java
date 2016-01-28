package org.briarproject.api;

import java.util.Arrays;

/** Type-safe wrapper for a byte array that uniquely identifies a device. */
public class DeviceId extends UniqueId {

	public DeviceId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof DeviceId && Arrays.equals(id, ((DeviceId) o).id);
	}
}
