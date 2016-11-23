package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.messaging.ConversationManager;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

@ThreadSafe
@NotNullByDefault
class ConversationManagerImpl implements ConversationManager {

	private final DatabaseComponent db;
	private final Set<ConversationClient> clients;

	@Inject
	ConversationManagerImpl(DatabaseComponent db) {
		this.db = db;
		clients = new CopyOnWriteArraySet<ConversationClient>();
	}

	@Override
	public void registerConversationClient(ConversationClient client) {
		if (!clients.add(client))
			throw new IllegalStateException("Client is already registered");
	}

	@Override
	public GroupCount getGroupCount(ContactId contactId) throws DbException {
		int msgCount = 0, unreadCount = 0;
		long latestTime = 0;
		Transaction txn = db.startTransaction(true);
		try {
			for (ConversationClient client : clients) {
				GroupCount count = client.getGroupCount(txn, contactId);
				msgCount += count.getMsgCount();
				unreadCount += count.getUnreadCount();
				if (count.getLatestMsgTime() > latestTime)
					latestTime = count.getLatestMsgTime();
			}
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return new GroupCount(msgCount, unreadCount, latestTime);
	}

}
