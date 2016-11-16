package org.briarproject.messaging;

import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.nullsafety.NotNullByDefault;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.inject.Inject;

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
		if (!clients.add(client)) {
			throw new IllegalStateException(
					"This client is already registered");
		}
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
