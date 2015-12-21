package org.briarproject.api.sync;

import org.briarproject.api.UniqueId;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a sync client.
 */
public class ClientId extends UniqueId {

	public ClientId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ClientId && Arrays.equals(id, ((ClientId) o).id);
	}
}
