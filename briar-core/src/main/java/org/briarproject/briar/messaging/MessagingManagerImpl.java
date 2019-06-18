package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.ContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.sync.validation.IncomingMessageHook;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.conversation.ConversationManager.ConversationClient;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.FileTooBigException;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.api.messaging.event.AttachmentReceivedEvent;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.util.IoUtils.copyAndClose;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
import static org.briarproject.briar.messaging.MessageTypes.ATTACHMENT;
import static org.briarproject.briar.messaging.MessageTypes.PRIVATE_MESSAGE;
import static org.briarproject.briar.messaging.MessagingConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_ATTACHMENT_HEADERS;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_CONTENT_TYPE;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_DESCRIPTOR_LENGTH;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_HAS_TEXT;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_MSG_TYPE;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_TIMESTAMP;

@Immutable
@NotNullByDefault
class MessagingManagerImpl implements MessagingManager, IncomingMessageHook,
		ConversationClient, OpenDatabaseHook, ContactHook,
		ClientVersioningHook {

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final MetadataParser metadataParser;
	private final MessageTracker messageTracker;
	private final ClientVersioningManager clientVersioningManager;
	private final ContactGroupFactory contactGroupFactory;

	@Inject
	MessagingManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			MetadataParser metadataParser, MessageTracker messageTracker,
			ContactGroupFactory contactGroupFactory) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.metadataParser = metadataParser;
		this.messageTracker = messageTracker;
		this.clientVersioningManager = clientVersioningManager;
		this.contactGroupFactory = contactGroupFactory;
	}

	@Override
	public GroupCount getGroupCount(Transaction txn, ContactId contactId)
			throws DbException {
		Contact contact = db.getContact(txn, contactId);
		GroupId groupId = getContactGroup(contact).getId();
		return messageTracker.getGroupCount(txn, groupId);
	}

	@Override
	public void setReadFlag(GroupId g, MessageId m, boolean read)
			throws DbException {
		messageTracker.setReadFlag(g, m, read);
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		// Create a local group to indicate that we've set this client up
		Group localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID,
				MAJOR_VERSION);
		if (db.containsGroup(txn, localGroup.getId())) return;
		db.addGroup(txn, localGroup);
		// Set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		db.addGroup(txn, g);
		// Apply the client's visibility to the contact group
		Visibility client = clientVersioningManager.getClientVisibility(txn,
				c.getId(), CLIENT_ID, MAJOR_VERSION);
		db.setGroupVisibility(txn, c.getId(), g.getId(), client);
		// Attach the contact ID to the group
		BdfDictionary d = new BdfDictionary();
		d.put(GROUP_KEY_CONTACT_ID, c.getId().getInt());
		try {
			clientHelper.mergeGroupMetadata(txn, g.getId(), d);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
		// Initialize the group count with current time
		messageTracker.initializeGroupCount(txn, g.getId());
	}

	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, c);
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public void onClientVisibilityChanging(Transaction txn, Contact c,
			Visibility v) throws DbException {
		// Apply the client's visibility to the contact group
		Group g = getContactGroup(c);
		db.setGroupVisibility(txn, c.getId(), g.getId(), v);
	}

	@Override
	public boolean incomingMessage(Transaction txn, Message m, Metadata meta)
			throws DbException, InvalidMessageException {
		try {
			BdfDictionary metaDict = metadataParser.parse(meta);
			// Message type is null for version 0.0 private messages
			Long messageType = metaDict.getOptionalLong(MSG_KEY_MSG_TYPE);
			if (messageType == null) {
				incomingPrivateMessage(txn, m, metaDict, true, emptyList());
			} else if (messageType == PRIVATE_MESSAGE) {
				boolean hasText = metaDict.getBoolean(MSG_KEY_HAS_TEXT);
				List<AttachmentHeader> headers =
						parseAttachmentHeaders(metaDict);
				incomingPrivateMessage(txn, m, metaDict, hasText, headers);
			} else if (messageType == ATTACHMENT) {
				incomingAttachment(txn, m);
			} else {
				throw new InvalidMessageException();
			}
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
		// Don't share message
		return false;
	}

	private void incomingPrivateMessage(Transaction txn, Message m,
			BdfDictionary meta, boolean hasText, List<AttachmentHeader> headers)
			throws DbException, FormatException {
		GroupId groupId = m.getGroupId();
		long timestamp = meta.getLong(MSG_KEY_TIMESTAMP);
		boolean local = meta.getBoolean(MSG_KEY_LOCAL);
		boolean read = meta.getBoolean(MSG_KEY_READ);
		PrivateMessageHeader header =
				new PrivateMessageHeader(m.getId(), groupId, timestamp, local,
						read, false, false, hasText, headers);
		ContactId contactId = getContactId(txn, groupId);
		PrivateMessageReceivedEvent event =
				new PrivateMessageReceivedEvent(header, contactId);
		txn.attach(event);
		messageTracker.trackIncomingMessage(txn, m);
	}

	private List<AttachmentHeader> parseAttachmentHeaders(BdfDictionary meta)
			throws FormatException {
		BdfList attachmentHeaders = meta.getList(MSG_KEY_ATTACHMENT_HEADERS);
		int length = attachmentHeaders.size();
		List<AttachmentHeader> headers = new ArrayList<>(length);
		for (int i = 0; i < length; i++) {
			BdfList header = attachmentHeaders.getList(i);
			MessageId id = new MessageId(header.getRaw(0));
			String contentType = header.getString(1);
			headers.add(new AttachmentHeader(id, contentType));
		}
		return headers;
	}

	private void incomingAttachment(Transaction txn, Message m)
			throws DbException {
		ContactId contactId = getContactId(txn, m.getGroupId());
		txn.attach(new AttachmentReceivedEvent(m.getId(), contactId));
	}

	@Override
	public void addLocalMessage(PrivateMessage m) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			BdfDictionary meta = new BdfDictionary();
			meta.put(MSG_KEY_TIMESTAMP, m.getMessage().getTimestamp());
			meta.put(MSG_KEY_LOCAL, true);
			meta.put(MSG_KEY_READ, true);
			if (!m.isLegacyFormat()) {
				meta.put(MSG_KEY_MSG_TYPE, PRIVATE_MESSAGE);
				meta.put(MSG_KEY_HAS_TEXT, m.hasText());
				BdfList headers = new BdfList();
				for (AttachmentHeader a : m.getAttachmentHeaders()) {
					headers.add(
							BdfList.of(a.getMessageId(), a.getContentType()));
				}
				meta.put(MSG_KEY_ATTACHMENT_HEADERS, headers);
			}
			// Mark attachments as shared and permanent now we're ready to send
			for (AttachmentHeader a : m.getAttachmentHeaders()) {
				db.setMessageShared(txn, a.getMessageId());
				db.setMessagePermanent(txn, a.getMessageId());
			}
			clientHelper.addLocalMessage(txn, m.getMessage(), meta, true,
					false);
			messageTracker.trackOutgoingMessage(txn, m.getMessage());
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new AssertionError(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public AttachmentHeader addLocalAttachment(GroupId groupId, long timestamp,
			String contentType, InputStream in)
			throws DbException, IOException {
		// TODO: Support large messages
		ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
		byte[] descriptor =
				clientHelper.toByteArray(BdfList.of(ATTACHMENT, contentType));
		bodyOut.write(descriptor);
		copyAndClose(in, bodyOut);
		if (bodyOut.size() > MAX_MESSAGE_BODY_LENGTH)
			throw new FileTooBigException();
		byte[] body = bodyOut.toByteArray();
		BdfDictionary meta = new BdfDictionary();
		meta.put(MSG_KEY_TIMESTAMP, timestamp);
		meta.put(MSG_KEY_LOCAL, true);
		meta.put(MSG_KEY_MSG_TYPE, ATTACHMENT);
		meta.put(MSG_KEY_CONTENT_TYPE, contentType);
		meta.put(MSG_KEY_DESCRIPTOR_LENGTH, descriptor.length);
		Message m = clientHelper.createMessage(groupId, timestamp, body);
		// Mark attachments as temporary, not shared until we're ready to send
		db.transaction(false, txn ->
				clientHelper.addLocalMessage(txn, m, meta, false, true));
		return new AttachmentHeader(m.getId(), contentType);
	}

	@Override
	public void removeAttachment(AttachmentHeader header) throws DbException {
		db.transaction(false,
				txn -> db.removeMessage(txn, header.getMessageId()));
	}

	private ContactId getContactId(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			return new ContactId(meta.getLong("contactId").intValue());
		} catch (FormatException e) {
			throw new DbException(e);
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
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return getContactGroup(contact).getId();
	}

	@Override
	public Collection<ConversationMessageHeader> getMessageHeaders(
			Transaction txn, ContactId c) throws DbException {
		Map<MessageId, BdfDictionary> metadata;
		Collection<MessageStatus> statuses;
		GroupId g;
		try {
			g = getContactGroup(db.getContact(txn, c)).getId();
			metadata = clientHelper.getMessageMetadataAsDictionary(txn, g);
			statuses = db.getMessageStatus(txn, c, g);
		} catch (FormatException e) {
			throw new DbException(e);
		}
		Collection<ConversationMessageHeader> headers = new ArrayList<>();
		for (MessageStatus s : statuses) {
			MessageId id = s.getMessageId();
			BdfDictionary meta = metadata.get(id);
			if (meta == null) continue;
			try {
				// Message type is null for version 0.0 private messages
				Long messageType = meta.getOptionalLong(MSG_KEY_MSG_TYPE);
				if (messageType != null && messageType != PRIVATE_MESSAGE)
					continue;
				long timestamp = meta.getLong(MSG_KEY_TIMESTAMP);
				boolean local = meta.getBoolean(MSG_KEY_LOCAL);
				boolean read = meta.getBoolean(MSG_KEY_READ);
				if (messageType == null) {
					headers.add(new PrivateMessageHeader(id, g, timestamp,
							local, read, s.isSent(), s.isSeen(), true,
							emptyList()));
				} else {
					boolean hasText = meta.getBoolean(MSG_KEY_HAS_TEXT);
					headers.add(new PrivateMessageHeader(id, g, timestamp,
							local, read, s.isSent(), s.isSeen(), hasText,
							parseAttachmentHeaders(meta)));
				}
			} catch (FormatException e) {
				throw new DbException(e);
			}
		}
		return headers;
	}

	@Override
	public String getMessageText(MessageId m) throws DbException {
		try {
			BdfList body = clientHelper.getMessageAsList(m);
			if (body.size() == 1) return body.getString(0); // Legacy format
			else return body.getOptionalString(1);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Attachment getAttachment(MessageId m) throws DbException {
		// TODO: Support large messages
		byte[] body = clientHelper.getMessage(m).getBody();
		try {
			BdfDictionary meta = clientHelper.getMessageMetadataAsDictionary(m);
			int offset = meta.getLong(MSG_KEY_DESCRIPTOR_LENGTH).intValue();
			return new Attachment(new ByteArrayInputStream(body, offset,
					body.length - offset));
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public boolean contactSupportsImages(Transaction txn, ContactId c)
			throws DbException {
		int minorVersion = clientVersioningManager
				.getClientMinorVersion(txn, c, CLIENT_ID, 0);
		// support was added in 0.1
		return minorVersion > 0;
	}

}
