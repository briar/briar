package org.briarproject.bramble.api.cleanup;

import org.briarproject.bramble.api.cleanup.event.CleanupTimerStartedEvent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.MessageId;

/**
 * The CleanupManager is responsible for tracking the cleanup deadlines of
 * messages and passing them to their respective
 * {@link CleanupHook CleanupHooks} when the deadlines are reached.
 * <p>
 * The CleanupManager responds to
 * {@link CleanupTimerStartedEvent CleanupTimerStartedEvents} broadcast by the
 * {@link DatabaseComponent}.
 * <p>
 * See {@link DatabaseComponent#setCleanupTimerDuration(Transaction, MessageId, long)},
 * {@link DatabaseComponent#startCleanupTimer(Transaction, MessageId)},
 * {@link DatabaseComponent#stopCleanupTimer(Transaction, MessageId)}.
 */
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
