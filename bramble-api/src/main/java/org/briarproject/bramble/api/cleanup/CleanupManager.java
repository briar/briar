package org.briarproject.bramble.api.cleanup;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

@NotNullByDefault
public interface CleanupManager {

	/**
	 * When scheduling a cleanup task we overshoot the deadline by this many
	 * milliseconds to reduce the number of tasks that need to be scheduled
	 * when messages have cleanup deadlines that are close together.
	 */
	long BATCH_DELAY_MS = 1000;

	/**
	 * Registers a hook to be called when messages are due for cleanup.
	 * This method should be called before
	 * {@link LifecycleManager#startServices(SecretKey)}.
	 */
	void registerCleanupHook(ClientId c, int majorVersion,
			CleanupHook hook);
}
