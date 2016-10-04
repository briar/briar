package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager.IncomingQueueMessageHook;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.QueueMessage;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.ValidationManager.IncomingMessageHook;

import static org.briarproject.api.clients.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.clients.BdfConstants.GROUP_KEY_LATEST_MSG;
import static org.briarproject.clients.BdfConstants.GROUP_KEY_MSG_COUNT;
import static org.briarproject.clients.BdfConstants.GROUP_KEY_UNREAD_COUNT;
import static org.briarproject.clients.BdfConstants.MSG_KEY_READ;

public abstract class BdfIncomingMessageHook implements IncomingMessageHook,
		IncomingQueueMessageHook, MessageTracker {

	protected final DatabaseComponent db;
	protected final ClientHelper clientHelper;
	protected final MetadataParser metadataParser;

	protected BdfIncomingMessageHook(DatabaseComponent db,
			ClientHelper clientHelper, MetadataParser metadataParser) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.metadataParser = metadataParser;
	}

	protected abstract boolean incomingMessage(Transaction txn, Message m,
			BdfList body, BdfDictionary meta) throws DbException,
			FormatException;

	@Override
	public boolean incomingMessage(Transaction txn, Message m, Metadata meta)
			throws DbException {
		return incomingMessage(txn, m, meta, MESSAGE_HEADER_LENGTH);
	}

	@Override
	public void incomingMessage(Transaction txn, QueueMessage q, Metadata meta)
			throws DbException {
		incomingMessage(txn, q, meta, QUEUE_MESSAGE_HEADER_LENGTH);
	}

	private boolean incomingMessage(Transaction txn, Message m, Metadata meta,
			int headerLength) throws DbException {
		try {
			byte[] raw = m.getRaw();
			BdfList body = clientHelper.toList(raw, headerLength,
					raw.length - headerLength);
			BdfDictionary metaDictionary = metadataParser.parse(meta);
			return incomingMessage(txn, m, body, metaDictionary);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	protected void trackIncomingMessage(Transaction txn, Message m)
			throws DbException {
		trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);
	}

	protected void trackOutgoingMessage(Transaction txn, Message m)
			throws DbException {
		trackMessage(txn, m.getGroupId(), m.getTimestamp(), true);
	}

	protected void trackMessage(Transaction txn, GroupId g, long time,
			boolean read) throws DbException {
		GroupCount c = getGroupCount(txn, g);
		long msgCount = c.getMsgCount() + 1;
		long unreadCount = c.getUnreadCount() + (read ? 0 : 1);
		long latestTime =
				time > c.getLatestMsgTime() ? time : c.getLatestMsgTime();
		storeGroupCount(txn, g,
				new GroupCount(msgCount, unreadCount, latestTime));
	}

	@Override
	public GroupCount getGroupCount(GroupId g) throws DbException {
		GroupCount count;
		Transaction txn = db.startTransaction(true);
		try {
			count = getGroupCount(txn, g);
			txn.setComplete();
		}
		finally {
			db.endTransaction(txn);
		}
		return count;
	}

	private GroupCount getGroupCount(Transaction txn, GroupId g)
			throws DbException {
		GroupCount count;
		try {
			BdfDictionary d = clientHelper.getGroupMetadataAsDictionary(txn, g);
			count = new GroupCount(
					d.getLong(GROUP_KEY_MSG_COUNT, 0L),
					d.getLong(GROUP_KEY_UNREAD_COUNT, 0L),
					d.getLong(GROUP_KEY_LATEST_MSG, 0L)
			);
		} catch (FormatException e) {
			throw new DbException(e);
		}
		return count;
	}

	private void storeGroupCount(Transaction txn, GroupId g, GroupCount c)
			throws DbException{
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
				BdfDictionary d = new BdfDictionary();
				d.put(GROUP_KEY_UNREAD_COUNT,
						c.getUnreadCount() + (read ? -1 : 1));
				clientHelper.mergeGroupMetadata(txn, g, d);
			}
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

}
