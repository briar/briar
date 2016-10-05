package org.briarproject.messaging;

import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.inject.Inject;

class ConversationManagerImpl implements ConversationManager {

	private final DatabaseComponent db;
	private final ContactGroupFactory contactGroupFactory;
	private final Set<ConversationClient> clients;

	@Inject
	ConversationManagerImpl(DatabaseComponent db,
			ContactGroupFactory contactGroupFactory) {
		this.db = db;
		this.contactGroupFactory = contactGroupFactory;
		clients = new CopyOnWriteArraySet<ConversationClient>();
	}

	@Override
	public void registerConversationClient(ConversationClient client) {
		clients.add(client);
	}

	@Override
	public GroupId getConversationId(ContactId contactId) throws DbException {
		// TODO we should probably transition this to its own group
		//      and/or work with the ContactId in the UI instead
		Contact contact;
		Transaction txn = db.startTransaction(true);
		try {
			contact = db.getContact(txn, contactId);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		Group group = contactGroupFactory
				.createContactGroup(MessagingManagerImpl.CLIENT_ID, contact);
		return group.getId();
	}

	@Override
	public GroupCount getGroupCount(ContactId contactId)
			throws DbException {

		long msgCount = 0, unreadCount = 0, latestTime = 0;
		Transaction txn = db.startTransaction(true);
		try {
			for (ConversationClient client : clients) {
				GroupCount count = client.getGroupCount(txn, contactId);
				msgCount += count.getMsgCount();
				unreadCount += count.getUnreadCount();
				if (count.getLatestMsgTime() > latestTime)
					latestTime = count.getLatestMsgTime();
			}
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return new GroupCount(msgCount, unreadCount, latestTime);
	}

}
