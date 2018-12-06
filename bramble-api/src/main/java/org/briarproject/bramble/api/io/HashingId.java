package org.briarproject.bramble.api.io;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a
 * {@link Message} while it's being hashed and the {@link MessageId} is not
 * yet known.
 */
@ThreadSafe
@NotNullByDefault
public class HashingId extends UniqueId {

	public HashingId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof HashingId && super.equals(o);
	}
}
