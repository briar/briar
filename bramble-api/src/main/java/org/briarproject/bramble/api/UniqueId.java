package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public abstract class UniqueId extends Bytes {

	/**
	 * The length of a unique identifier in bytes.
	 */
	public static final int LENGTH = 32;

	protected UniqueId(byte[] id) {
		super(id);
		if (id.length != LENGTH) throw new IllegalArgumentException();
	}
}
