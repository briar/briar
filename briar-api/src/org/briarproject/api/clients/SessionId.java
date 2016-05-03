package org.briarproject.api.clients;

import org.briarproject.api.sync.MessageId;

/**
 * Type-safe wrapper for a byte array
 * that uniquely identifies a protocol session.
 */
public class SessionId extends MessageId {

	public SessionId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SessionId && super.equals(o);
	}
}
