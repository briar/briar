package org.briarproject.bramble.api.cleanup;

import org.briarproject.bramble.api.cleanup.event.MessagesCleanedUpEvent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

/**
 * An interface for registering a hook with the {@link CleanupManager}
 * that will be called when a message's cleanup deadline is reached.
 */
@NotNullByDefault
public interface CleanupHook {

	/**
	 * Called when a message's cleanup deadline is reached. If this method
	 * returns true, a {@link MessagesCleanedUpEvent} will be broadcast.
	 *
	 * @return True if the message has been deleted, or false if it has not,
	 * in which case the message's cleanup timer will be stopped
	 */
	boolean deleteMessage(Transaction txn, GroupId g, MessageId m)
			throws DbException;
}
