package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ReadableMessageManager;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.clients.ReadableMessageConstants.LOCAL;
import static org.briarproject.api.clients.ReadableMessageConstants.READ;
import static org.briarproject.api.clients.ReadableMessageConstants.TIMESTAMP;
import static org.briarproject.api.clients.ReadableMessageConstants.UNREAD;

public abstract class ReadableMessageManagerImpl
		extends BdfIncomingMessageHook implements ReadableMessageManager {

	protected final DatabaseComponent db;

	protected ReadableMessageManagerImpl(ClientHelper clientHelper,
			DatabaseComponent db, MetadataParser metadataParser) {
		super(clientHelper, metadataParser);

		this.db = db;
	}

	protected abstract Group getContactGroup(Contact c);

	protected abstract void incomingReadableMessage(Transaction txn,
			Message m, BdfList body, BdfDictionary meta)
			throws DbException, FormatException, InvalidMessageException;

	@Override
	protected void incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		// Check if we accept this message
		try {
			incomingReadableMessage(txn, m, body, meta);

			// Update the group timestamp and unread count
			GroupId groupId = m.getGroupId();
			long timestamp = meta.getLong(TIMESTAMP);
			boolean local = meta.getBoolean(LOCAL);
			boolean read = meta.getBoolean(READ);
			updateGroupMetadata(txn, groupId, timestamp, local, read, read);
		} catch (InvalidMessageException ignored) {
		}
	}

	@Override
	public long getTimestamp(ContactId c) throws DbException {
		BdfDictionary meta;
		Transaction txn = db.startTransaction(true);
		try {
			GroupId g = getContactGroup(db.getContact(txn, c)).getId();
			meta = clientHelper.getGroupMetadataAsDictionary(txn, g);
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return meta.getLong(TIMESTAMP, -1L);
	}

	@Override
	public int getUnreadCount(ContactId c) throws DbException {
		BdfDictionary meta;
		Transaction txn = db.startTransaction(true);
		try {
			GroupId g = getContactGroup(db.getContact(txn, c)).getId();
			meta = clientHelper.getGroupMetadataAsDictionary(txn, g);
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return meta.getLong(UNREAD, 0L).intValue();
	}

	@Override
	public void setReadFlag(ContactId c, MessageId m, boolean local,
			boolean read) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			boolean wasRead =
					clientHelper.getMessageMetadataAsDictionary(txn, m)
							.getBoolean(READ);
			BdfDictionary meta = new BdfDictionary();
			meta.put(READ, read);
			clientHelper.mergeMessageMetadata(txn, m, meta);
			GroupId g = getContactGroup(db.getContact(txn, c)).getId();
			updateGroupMetadata(txn, g, -1, local, wasRead, read);
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private void updateGroupMetadata(Transaction txn, GroupId groupId,
			long timestamp, boolean local, boolean wasRead, boolean read)
			throws DbException, FormatException {
		BdfDictionary groupMeta =
				clientHelper.getGroupMetadataAsDictionary(txn, groupId);
		long groupTimestamp = groupMeta.getLong(TIMESTAMP, -1L);
		int unread = groupMeta.getLong(UNREAD, 0L).intValue();
		BdfDictionary d = new BdfDictionary();
		if (timestamp > groupTimestamp) {
			d.put(TIMESTAMP, timestamp);
		}
		if (!local && (wasRead != read)) {
			d.put(UNREAD, read ? (unread > 0 ? unread - 1 : 0) : unread + 1);
		}
		clientHelper.mergeGroupMetadata(txn, groupId, d);
	}
}
