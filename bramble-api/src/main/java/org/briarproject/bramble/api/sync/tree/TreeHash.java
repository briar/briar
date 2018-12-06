package org.briarproject.bramble.api.sync.tree;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a sequence of
 * one or more message blocks.
 */
@ThreadSafe
@NotNullByDefault
public class TreeHash extends UniqueId {

	public TreeHash(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TreeHash && super.equals(o);
	}
}
