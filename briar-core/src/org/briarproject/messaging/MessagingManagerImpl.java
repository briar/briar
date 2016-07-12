package org.briarproject.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.Client;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager.AddContactHook;
import org.briarproject.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.PrivateMessageReceivedEvent;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.clients.ReadableMessageManagerImpl;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.inject.Inject;

import static org.briarproject.api.clients.ReadableMessageConstants.LOCAL;
import static org.briarproject.api.clients.ReadableMessageConstants.READ;
import static org.briarproject.api.clients.ReadableMessageConstants.TIMESTAMP;

class MessagingManagerImpl extends ReadableMessageManagerImpl
		implements MessagingManager, Client, AddContactHook, RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"6bcdc006c0910b0f44e40644c3b31f1a"
					+ "8bf9a6d6021d40d219c86b731b903070"));

	private final PrivateGroupFactory privateGroupFactory;

	@Inject
	MessagingManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			MetadataParser metadataParser,
			PrivateGroupFactory privateGroupFactory) {
		super(clientHelper, db, metadataParser);

		this.privateGroupFactory = privateGroupFactory;
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		// Ensure we've set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		try {
			// Create a group to share with the contact
			Group g = getContactGroup(c);
			// Return if we've already set things up for this contact
			if (db.containsGroup(txn, g.getId())) return;
			// Store the group and share it with the contact
			db.addGroup(txn, g);
			db.setVisibleToContact(txn, c.getId(), g.getId(), true);
			// Attach the contact ID to the group
			BdfDictionary d = new BdfDictionary();
			d.put("contactId", c.getId().getInt());
			clientHelper.mergeGroupMetadata(txn, g.getId(), d);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Group getContactGroup(Contact c) {
		return privateGroupFactory.createPrivateGroup(CLIENT_ID, c);
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	protected void incomingReadableMessage(Transaction txn, Message m,
			BdfList body, BdfDictionary meta)
			throws DbException, FormatException, InvalidMessageException {

		// Broadcast event
		GroupId groupId = m.getGroupId();
		long timestamp = meta.getLong(TIMESTAMP);
		String contentType = meta.getString("contentType");
		boolean local = meta.getBoolean(LOCAL);
		boolean read = meta.getBoolean(READ);
		PrivateMessageHeader header = new PrivateMessageHeader(
				m.getId(), timestamp, contentType, local, read, false, false);
		PrivateMessageReceivedEvent event = new PrivateMessageReceivedEvent(
				header, groupId);
		txn.attach(event);
	}

	@Override
	public void addLocalMessage(PrivateMessage m) throws DbException {
		try {
			BdfDictionary meta = new BdfDictionary();
			meta.put(TIMESTAMP, m.getMessage().getTimestamp());
			if (m.getParent() != null) meta.put("parent", m.getParent());
			meta.put("contentType", m.getContentType());
			meta.put(LOCAL, true);
			meta.put(READ, true);
			clientHelper.addLocalMessage(m.getMessage(), CLIENT_ID, meta, true);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ContactId getContactId(GroupId g) throws DbException {
		try {
			BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(g);
			return new ContactId(meta.getLong("contactId").intValue());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public GroupId getConversationId(ContactId c) throws DbException {
		Contact contact;
		Transaction txn = db.startTransaction(true);
		try {
			contact = db.getContact(txn, c);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return getContactGroup(contact).getId();
	}

	@Override
	public Collection<PrivateMessageHeader> getMessageHeaders(ContactId c)
			throws DbException {
		Map<MessageId, BdfDictionary> metadata;
		Collection<MessageStatus> statuses;
		Transaction txn = db.startTransaction(true);
		try {
			GroupId g = getContactGroup(db.getContact(txn, c)).getId();
			metadata = clientHelper.getMessageMetadataAsDictionary(txn, g);
			statuses = db.getMessageStatus(txn, c, g);
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		Collection<PrivateMessageHeader> headers =
				new ArrayList<PrivateMessageHeader>();
		for (MessageStatus s : statuses) {
			MessageId id = s.getMessageId();
			BdfDictionary meta = metadata.get(id);
			if (meta == null) continue;
			try {
				long timestamp = meta.getLong(TIMESTAMP);
				String contentType = meta.getString("contentType");
				boolean local = meta.getBoolean(LOCAL);
				boolean read = meta.getBoolean(READ);
				headers.add(new PrivateMessageHeader(id, timestamp, contentType,
						local, read, s.isSent(), s.isSeen()));
			} catch (FormatException e) {
				throw new DbException(e);
			}
		}
		return headers;
	}

	@Override
	public byte[] getMessageBody(MessageId m) throws DbException {
		try {
			// Parent ID, content type, private message body
			BdfList message = clientHelper.getMessageAsList(m);
			return message.getRaw(2);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}
}
