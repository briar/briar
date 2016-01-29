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
import org.briarproject.api.db.NoSuchContactException;
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
import java.util.Collections;
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
			BdfReaderFactory bdfReaderFactory,
			MetadataEncoder metadataEncoder,
			MetadataParser metadataParser) {
		this.db = db;
		this.privateGroupFactory = privateGroupFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.metadataEncoder = metadataEncoder;
		this.metadataParser = metadataParser;
	}

	@Override
	public void addingContact(ContactId c) {
		try {
			// Create the conversation group
			Group g = getConversationGroup(db.getContact(c));
			// Subscribe to the group and share it with the contact
			db.addGroup(g);
			db.addContactGroup(c, g);
			db.setVisibility(g.getId(), Collections.singletonList(c));
			// Attach the contact ID to the group
			BdfDictionary d = new BdfDictionary();
			d.put("contactId", c.getInt());
			db.mergeGroupMetadata(g.getId(), metadataEncoder.encode(d));
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private Group getConversationGroup(Contact c) {
		return privateGroupFactory.createPrivateGroup(CLIENT_ID, c);
	}

	@Override
	public void removingContact(ContactId c) {
		try {
			db.removeGroup(getConversationGroup(db.getContact(c)));
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void addLocalMessage(PrivateMessage m) throws DbException {
		BdfDictionary d = new BdfDictionary();
		d.put("timestamp", m.getMessage().getTimestamp());
		if (m.getParent() != null) d.put("parent", m.getParent().getBytes());
		d.put("contentType", m.getContentType());
		d.put("local", true);
		d.put("read", true);
		try {
			Metadata meta = metadataEncoder.encode(d);
			db.addLocalMessage(m.getMessage(), CLIENT_ID, meta, true);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ContactId getContactId(GroupId g) throws DbException {
		try {
			BdfDictionary d = metadataParser.parse(db.getGroupMetadata(g));
			long id = d.getInteger("contactId");
			return new ContactId((int) id);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			throw new NoSuchContactException();
		}
	}

	@Override
	public GroupId getConversationId(ContactId c) throws DbException {
		return getConversationGroup(db.getContact(c)).getId();
	}

	@Override
	public Collection<PrivateMessageHeader> getMessageHeaders(ContactId c)
			throws DbException {
		GroupId groupId = getConversationId(c);
		Map<MessageId, Metadata> metadata = db.getMessageMetadata(groupId);
		Collection<MessageStatus> statuses = db.getMessageStatus(c, groupId);
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
		byte[] raw = db.getRawMessage(m);
		ByteArrayInputStream in = new ByteArrayInputStream(raw,
				MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
		BdfReader r = bdfReaderFactory.createReader(in);
		try {
			// Extract the private message body
			r.readListStart();
			if (r.hasRaw()) r.skipRaw(); // Parent ID
			else r.skipNull(); // No parent
			r.skipString(); // Content type
			return r.readRaw(MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		} catch (FormatException e) {
			// Not a valid private message
			throw new IllegalArgumentException();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setReadFlag(MessageId m, boolean read) throws DbException {
		BdfDictionary d = new BdfDictionary();
		d.put("read", read);
		try {
			db.mergeMessageMetadata(m, metadataEncoder.encode(d));
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}
}
