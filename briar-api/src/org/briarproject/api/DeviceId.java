package org.briarproject.api;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a device.
 */
public class DeviceId extends UniqueId {

	public DeviceId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof DeviceId && super.equals(o);
	}
}
