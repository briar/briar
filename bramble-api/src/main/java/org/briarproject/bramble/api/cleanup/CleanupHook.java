package org.briarproject.bramble.api.cleanup;

import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import java.util.Collection;

/**
 * An interface for registering a hook with the {@link CleanupManager}
 * that will be called when a message's cleanup deadline is reached.
 */
@NotNullByDefault
public interface CleanupHook {

	/**
	 * Called when the cleanup deadlines of one or more messages are reached.
	 * <p>
	 * The callee is not required to delete the messages, but the hook won't be
	 * called again for these messages unless another cleanup timer is set (see
	 * {@link DatabaseComponent#setCleanupTimerDuration(Transaction, MessageId, long)}
	 * and {@link DatabaseComponent#startCleanupTimer(Transaction, MessageId)}).
	 */
	void deleteMessages(Transaction txn, GroupId g,
			Collection<MessageId> messageIds) throws DbException;
}
