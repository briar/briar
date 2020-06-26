package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * An interface for handling a {@link Priority} record received by an
 * incoming {@link SyncSession}.
 */
@NotNullByDefault
public interface PriorityHandler {

	void handle(Priority p);
}
