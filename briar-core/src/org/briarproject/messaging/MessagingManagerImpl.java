package org.briarproject.messaging;

import com.google.inject.Inject;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager.AddContactHook;
import org.briarproject.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.sync.PrivateGroupFactory;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class MessagingManagerImpl implements MessagingManager, AddContactHook,
		RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"6bcdc006c0910b0f44e40644c3b31f1a"
					+ "8bf9a6d6021d40d219c86b731b903070"));

	private static final Logger LOG =
			Logger.getLogger(MessagingManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final PrivateGroupFactory privateGroupFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final MetadataEncoder metadataEncoder;
	private final MetadataParser metadataParser;

	@Inject
	MessagingManagerImpl(DatabaseComponent db,
			PrivateGroupFactory privateGroupFactory,
			BdfReaderFactory bdfReaderFactory, MetadataEncoder metadataEncoder,
			MetadataParser metadataParser) {
		this.db = db;
		this.privateGroupFactory = privateGroupFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.metadataEncoder = metadataEncoder;
		this.metadataParser = metadataParser;
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		try {
			// Create a group to share with the contact
			Group g = getContactGroup(c);
			// Store the group and share it with the contact
			db.addGroup(txn, g);
			db.setVisibleToContact(txn, c.getId(), g.getId(), true);
			// Attach the contact ID to the group
			BdfDictionary d = new BdfDictionary();
			d.put("contactId", c.getId().getInt());
			db.mergeGroupMetadata(txn, g.getId(), metadataEncoder.encode(d));
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private Group getContactGroup(Contact c) {
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
	public void addLocalMessage(PrivateMessage m) throws DbException {
		try {
			BdfDictionary d = new BdfDictionary();
			d.put("timestamp", m.getMessage().getTimestamp());
			if (m.getParent() != null)
				d.put("parent", m.getParent().getBytes());
			d.put("contentType", m.getContentType());
			d.put("local", true);
			d.put("read", true);
			Metadata meta = metadataEncoder.encode(d);
			Transaction txn = db.startTransaction();
			try {
				db.addLocalMessage(txn, m.getMessage(), CLIENT_ID, meta, true);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ContactId getContactId(GroupId g) throws DbException {
		try {
			Metadata meta;
			Transaction txn = db.startTransaction();
			try {
				meta = db.getGroupMetadata(txn, g);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			BdfDictionary d = metadataParser.parse(meta);
			return new ContactId(d.getInteger("contactId").intValue());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public GroupId getConversationId(ContactId c) throws DbException {
		Contact contact;
		Transaction txn = db.startTransaction();
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
		GroupId g = getConversationId(c);
		Map<MessageId, Metadata> metadata;
		Collection<MessageStatus> statuses;
		Transaction txn = db.startTransaction();
		try {
			metadata = db.getMessageMetadata(txn, g);
			statuses = db.getMessageStatus(txn, c, g);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		Collection<PrivateMessageHeader> headers =
				new ArrayList<PrivateMessageHeader>();
		for (MessageStatus s : statuses) {
			MessageId id = s.getMessageId();
			Metadata m = metadata.get(id);
			if (m == null) continue;
			try {
				BdfDictionary d = metadataParser.parse(m);
				long timestamp = d.getInteger("timestamp");
				String contentType = d.getString("contentType");
				boolean local = d.getBoolean("local");
				boolean read = d.getBoolean("read");
				headers.add(new PrivateMessageHeader(id, timestamp, contentType,
						local, read, s.isSent(), s.isSeen()));
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		return headers;
	}

	@Override
	public byte[] getMessageBody(MessageId m) throws DbException {
		byte[] raw;
		Transaction txn = db.startTransaction();
		try {
			raw = db.getRawMessage(txn, m);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		ByteArrayInputStream in = new ByteArrayInputStream(raw,
				MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
		BdfReader r = bdfReaderFactory.createReader(in);
		try {
			r.readListStart();
			if (r.hasRaw()) r.skipRaw(); // Parent ID
			else r.skipNull(); // No parent
			r.skipString(); // Content type
			byte[] messageBody = r.readRaw(MAX_PRIVATE_MESSAGE_BODY_LENGTH);
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			return messageBody;
		} catch (FormatException e) {
			throw new DbException(e);
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setReadFlag(MessageId m, boolean read) throws DbException {
		try {
			BdfDictionary d = new BdfDictionary();
			d.put("read", read);
			Metadata meta = metadataEncoder.encode(d);
			Transaction txn = db.startTransaction();
			try {
				db.mergeMessageMetadata(txn, m, meta);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}
}
