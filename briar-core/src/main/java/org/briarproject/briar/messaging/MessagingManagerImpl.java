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
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.bramble.util.IoUtils;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.FileTooBigException;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;
import org.briarproject.briar.client.ConversationClientImpl;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;

@Immutable
@NotNullByDefault
class MessagingManagerImpl extends ConversationClientImpl
		implements MessagingManager, OpenDatabaseHook, ContactHook,
		ClientVersioningHook {

	private final ClientVersioningManager clientVersioningManager;
	private final ContactGroupFactory contactGroupFactory;
	private final MessageFactory messageFactory;

	@Inject
	MessagingManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			MetadataParser metadataParser, MessageTracker messageTracker,
			ContactGroupFactory contactGroupFactory,
			MessageFactory messageFactory) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.clientVersioningManager = clientVersioningManager;
		this.contactGroupFactory = contactGroupFactory;
		this.messageFactory = messageFactory;
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
		d.put("contactId", c.getId().getInt());
		try {
			clientHelper.mergeGroupMetadata(txn, g.getId(), d);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
		// Initialize the group count with current time
		initializeGroupCount(txn, g.getId());
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
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		GroupId groupId = m.getGroupId();
		long timestamp = meta.getLong("timestamp");
		boolean local = meta.getBoolean("local");
		boolean read = meta.getBoolean(MSG_KEY_READ);
		PrivateMessageHeader header =
				new PrivateMessageHeader(m.getId(), groupId, timestamp, local,
						read, false, false, true, emptyList());
		ContactId contactId = getContactId(txn, groupId);
		PrivateMessageReceivedEvent event =
				new PrivateMessageReceivedEvent(header, contactId);
		txn.attach(event);
		messageTracker.trackIncomingMessage(txn, m);

		// don't share message
		return false;
	}

	@Override
	public void addLocalMessage(PrivateMessage m) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			BdfDictionary meta = new BdfDictionary();
			meta.put("timestamp", m.getMessage().getTimestamp());
			meta.put("local", true);
			meta.put("read", true);
			clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
			messageTracker.trackOutgoingMessage(txn, m.getMessage());
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public AttachmentHeader addLocalAttachment(GroupId groupId, long timestamp,
			String contentType, InputStream is)
			throws DbException, IOException {
		// TODO add real implementation
		byte[] body = new byte[MAX_MESSAGE_BODY_LENGTH];
		try {
			IoUtils.read(is, body);
		} catch (EOFException ignored) {
		}
		if (is.available() > 0) throw new FileTooBigException();
		is.close();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException ignored) {
		}
		Message m = messageFactory.createMessage(groupId, timestamp, body);
		clientHelper.addLocalMessage(m, new BdfDictionary(), false);
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
				long timestamp = meta.getLong("timestamp");
				boolean local = meta.getBoolean("local");
				boolean read = meta.getBoolean("read");
				headers.add(new PrivateMessageHeader(id, g, timestamp, local,
						read, s.isSent(), s.isSeen(), true, emptyList()));
			} catch (FormatException e) {
				throw new DbException(e);
			}
		}
		return headers;
	}

	@Override
	public String getMessageText(MessageId m) throws DbException {
		try {
			// 0: private message text
			return clientHelper.getMessageAsList(m).getString(0);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Attachment getAttachment(MessageId mId) throws DbException {
		// TODO add real implementation
		Message m = clientHelper.getMessage(mId);
		byte[] bytes = m.getBody();
		return new Attachment(new ByteArrayInputStream(bytes));
	}

	@Override
	public boolean contactSupportsImages(Transaction txn, ContactId c)
			throws DbException {
		int minorVersion = clientVersioningManager
				.getClientMinorVersion(txn, c, CLIENT_ID, 0);
		// support was added in 0.1
		return minorVersion == 1;
	}

}
