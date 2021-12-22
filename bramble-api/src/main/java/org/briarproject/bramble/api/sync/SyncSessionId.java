package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a sync session.
 * These random identifiers are used locally to label persistent state
 * associated with a session.
 */
@ThreadSafe
@NotNullByDefault
public class SyncSessionId extends UniqueId {

	public SyncSessionId(byte[] id) {
		super(id);
	}
}
