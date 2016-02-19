package org.briarproject.api.sync;

import org.briarproject.api.UniqueId;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a sync client.
 */
public class ClientId extends UniqueId {

	public ClientId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ClientId && super.equals(o);
	}
}
