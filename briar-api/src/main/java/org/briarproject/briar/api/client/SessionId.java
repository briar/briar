package org.briarproject.briar.api.client;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a protocol
 * session.
 */
@ThreadSafe
@NotNullByDefault
public class SessionId extends UniqueId {

	public SessionId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SessionId && super.equals(o);
	}
}
