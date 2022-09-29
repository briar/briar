package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a
 * {@link PendingContact}.
 */
@ThreadSafe
@NotNullByDefault
public class PendingContactId extends UniqueId {

	public PendingContactId(byte[] id) {
		super(id);
	}
}
