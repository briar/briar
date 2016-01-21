package org.briarproject.api.introduction;

import org.briarproject.api.sync.MessageId;

/**
 * Type-safe wrapper for a byte array that uniquely identifies an
 * introduction session.
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
