package org.briarproject.messaging;

import com.google.inject.Inject;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateConversation;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageHeader;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

// Temporary facade during sync protocol refactoring
class MessagingManagerImpl implements MessagingManager {

	private final DatabaseComponent db;

	@Inject
	MessagingManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public void addLocalMessage(Message m) throws DbException {
		db.addLocalMessage(m);
	}

	@Override
	public PrivateConversation getConversation(GroupId g) throws DbException {
		return new PrivateConversationImpl(db.getGroup(g));
	}

	@Override
	public GroupId getConversationId(ContactId c) throws DbException {
		return db.getInboxGroupId(c);
	}

	@Override
	public Collection<MessageHeader> getMessageHeaders(ContactId c)
			throws DbException {
		return db.getInboxMessageHeaders(c);
	}

	@Override
	public byte[] getMessageBody(MessageId m) throws DbException {
		return db.getMessageBody(m);
	}

	@Override
	public void setConversation(ContactId c, PrivateConversation p)
			throws DbException {
		db.setInboxGroup(c, ((PrivateConversationImpl) p).getGroup());
	}

	@Override
	public void setReadFlag(MessageId m, boolean read) throws DbException {
		db.setReadFlag(m, read);
	}
}
