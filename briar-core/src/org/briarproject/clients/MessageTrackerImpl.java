package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import javax.inject.Inject;

import static org.briarproject.clients.BdfConstants.GROUP_KEY_LATEST_MSG;
import static org.briarproject.clients.BdfConstants.GROUP_KEY_MSG_COUNT;
import static org.briarproject.clients.BdfConstants.GROUP_KEY_UNREAD_COUNT;
import static org.briarproject.clients.BdfConstants.MSG_KEY_READ;

@NotNullByDefault
class MessageTrackerImpl implements MessageTracker {

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;

	@Inject
	MessageTrackerImpl(DatabaseComponent db, ClientHelper clientHelper) {
		this.db = db;
		this.clientHelper = clientHelper;
	}

	@Override
	public void trackIncomingMessage(Transaction txn, Message m)
			throws DbException {
		trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);
	}

	@Override
	public void trackOutgoingMessage(Transaction txn, Message m)
			throws DbException {
		trackMessage(txn, m.getGroupId(), m.getTimestamp(), true);
	}

	@Override
	public void trackMessage(Transaction txn, GroupId g, long time,
			boolean read) throws DbException {
		GroupCount c = getGroupCount(txn, g);
		int msgCount = c.getMsgCount() + 1;
		int unreadCount = c.getUnreadCount() + (read ? 0 : 1);
		long latestMsgTime = Math.max(c.getLatestMsgTime(), time);
		storeGroupCount(txn, g, new GroupCount(msgCount, unreadCount,
				latestMsgTime));
	}

	@Override
	public GroupCount getGroupCount(GroupId g) throws DbException {
		GroupCount count;
		Transaction txn = db.startTransaction(true);
		try {
			count = getGroupCount(txn, g);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return count;
	}

	@Override
	public GroupCount getGroupCount(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary d = clientHelper.getGroupMetadataAsDictionary(txn, g);
			return new GroupCount(
					d.getLong(GROUP_KEY_MSG_COUNT, 0L).intValue(),
					d.getLong(GROUP_KEY_UNREAD_COUNT, 0L).intValue(),
					d.getLong(GROUP_KEY_LATEST_MSG, 0L)
			);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void storeGroupCount(Transaction txn, GroupId g, GroupCount c)
			throws DbException {
		try {
			BdfDictionary d = BdfDictionary.of(
					new BdfEntry(GROUP_KEY_MSG_COUNT, c.getMsgCount()),
					new BdfEntry(GROUP_KEY_UNREAD_COUNT, c.getUnreadCount()),
					new BdfEntry(GROUP_KEY_LATEST_MSG, c.getLatestMsgTime())
			);
			clientHelper.mergeGroupMetadata(txn, g, d);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void setReadFlag(GroupId g, MessageId m, boolean read)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			// check current read status of message
			BdfDictionary old =
					clientHelper.getMessageMetadataAsDictionary(txn, m);
			boolean wasRead = old.getBoolean(MSG_KEY_READ, false);

			// if status changed
			if (wasRead != read) {
				// mark individual message as read
				BdfDictionary meta = new BdfDictionary();
				meta.put(MSG_KEY_READ, read);
				clientHelper.mergeMessageMetadata(txn, m, meta);

				// update unread counter in group metadata
				GroupCount c = getGroupCount(txn, g);
				int unreadCount = c.getUnreadCount() + (read ? -1 : 1);
				if (unreadCount < 0) throw new DbException();
				storeGroupCount(txn, g, new GroupCount(c.getMsgCount(),
						unreadCount, c.getLatestMsgTime()));
			}
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

}
