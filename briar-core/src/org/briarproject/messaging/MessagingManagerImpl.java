package org.briarproject.messaging;

import com.google.inject.Inject;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateConversation;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageHeader;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// Temporary facade during sync protocol refactoring
class MessagingManagerImpl implements MessagingManager {

	private final DatabaseComponent db;
	private final CryptoComponent crypto;
	private final GroupFactory groupFactory;

	@Inject
	MessagingManagerImpl(DatabaseComponent db, CryptoComponent crypto,
			GroupFactory groupFactory) {
		this.db = db;
		this.crypto = crypto;
		this.groupFactory = groupFactory;
	}

	@Override
	public void addContact(ContactId c, SecretKey master) throws DbException {
		byte[] salt = crypto.deriveGroupSalt(master);
		Group inbox = groupFactory.createGroup("Inbox", salt);
		db.addGroup(inbox);
		db.setInboxGroup(c, inbox);
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
	public Collection<PrivateMessageHeader> getMessageHeaders(ContactId c)
			throws DbException {
		Collection<MessageHeader> headers = db.getInboxMessageHeaders(c);
		List<PrivateMessageHeader> privateHeaders =
				new ArrayList<PrivateMessageHeader>(headers.size());
		for (MessageHeader m : headers)
			privateHeaders.add(new PrivateMessageHeaderImpl(m));
		return Collections.unmodifiableList(privateHeaders);
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
