package org.briarproject.briar.api.client;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.Nullable;

@NotNullByDefault
public interface MessageTracker {

	/**
	 * Gets the number of visible and unread messages in the group
	 * as well as the timestamp of the latest message
	 **/
	GroupCount getGroupCount(GroupId g) throws DbException;

	/**
	 * Gets the number of visible and unread messages in the group
	 * as well as the timestamp of the latest message
	 **/
	GroupCount getGroupCount(Transaction txn, GroupId g) throws DbException;

	/**
	 * Updates the group count for the given incoming message.
	 */
	void trackIncomingMessage(Transaction txn, Message m) throws DbException;

	/**
	 * Updates the group count for the given outgoing message.
	 */
	void trackOutgoingMessage(Transaction txn, Message m) throws DbException;

	/**
	 * Updates the group count for the given message.
	 */
	void trackMessage(Transaction txn, GroupId g, long timestamp, boolean read)
			throws DbException;

	/**
	 *  Loads the stored message id for the respective group id or returns null
	 *  if none is available.
	 */
	@Nullable
	MessageId loadStoredMessageId(GroupId g) throws DbException;

	/**
	 * Stores the message id for the respective group id. Exactly one message id
	 * can be stored for any group id at any time, older values are overwritten.
	 */
	void storeMessageId(GroupId g, MessageId m) throws DbException;

	/**
	 * Marks a message as read or unread and updates the group count.
	 */
	void setReadFlag(GroupId g, MessageId m, boolean read) throws DbException;

	class GroupCount {

		private final int msgCount, unreadCount;
		private final long latestMsgTime;

		public GroupCount(int msgCount, int unreadCount, long latestMsgTime) {
			this.msgCount = msgCount;
			this.unreadCount = unreadCount;
			this.latestMsgTime = latestMsgTime;
		}

		public int getMsgCount() {
			return msgCount;
		}

		public int getUnreadCount() {
			return unreadCount;
		}

		public long getLatestMsgTime() {
			return latestMsgTime;
		}
	}

}
