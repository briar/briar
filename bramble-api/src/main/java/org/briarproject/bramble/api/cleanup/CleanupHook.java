package org.briarproject.bramble.api.cleanup;

import org.briarproject.bramble.api.cleanup.event.MessagesCleanedUpEvent;
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
	 * When this method returns, a {@link MessagesCleanedUpEvent} will be
	 * broadcast.
	 */
	void deleteMessages(Transaction txn, GroupId g,
			Collection<MessageId> messageIds) throws DbException;
}
