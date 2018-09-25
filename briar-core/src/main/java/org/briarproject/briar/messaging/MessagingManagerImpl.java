package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.ContactManager.ContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.introduction.event.IntroductionSucceededEvent;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;
import org.briarproject.briar.client.ConversationClientImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;

@Immutable
@NotNullByDefault
class MessagingManagerImpl extends ConversationClientImpl
		implements MessagingManager, Client, ContactHook, ClientVersioningHook {

	private final ClientVersioningManager clientVersioningManager;
	private final ContactGroupFactory contactGroupFactory;

	// TODO remove
	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final AuthorFactory authorFactory;

	@Inject
	MessagingManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			MetadataParser metadataParser, MessageTracker messageTracker,
			ContactGroupFactory contactGroupFactory,
			ContactManager contactManager, IdentityManager identityManager,
			AuthorFactory authorFactory) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.clientVersioningManager = clientVersioningManager;
		this.contactGroupFactory = contactGroupFactory;

		// TODO remove
		this.contactManager = contactManager;
		this.identityManager = identityManager;
		this.authorFactory = authorFactory;
	}

	private static final String PENDING_CONTACTS = "PENDING_CONTACTS";
	@Override
	public void addNewPendingContact(String name, long timestamp)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			BdfList list = getPendingContacts(txn);
			BdfDictionary contact = new BdfDictionary();
			contact.put("name", name);
			contact.put("timestamp", timestamp);
			list.add(contact);

			Group localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID,
					MAJOR_VERSION);
			BdfDictionary meta =
					BdfDictionary.of(new BdfEntry(PENDING_CONTACTS, list));
			clientHelper.mergeGroupMetadata(txn, localGroup.getId(), meta);

			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} finally {
			db.endTransaction(txn);
		}
	}
	@Override
	public void removePendingContact(String name, long timestamp) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			BdfList list = getPendingContacts(txn);

			BdfDictionary contactDict = new BdfDictionary();
			contactDict.put("name", name);
			contactDict.put("timestamp", timestamp);
			list.remove(contactDict);

			Group localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID,
					MAJOR_VERSION);
			BdfDictionary meta =
					BdfDictionary.of(new BdfEntry(PENDING_CONTACTS, list));
			clientHelper.mergeGroupMetadata(txn, localGroup.getId(), meta);

			AuthorId local = identityManager.getLocalAuthor(txn).getId();
			Author remote = authorFactory
					.createAuthor(name, new byte[MAX_PUBLIC_KEY_LENGTH]);
			contactManager.addContact(txn, remote, local, false, true);

			Contact contact =
					contactManager.getContact(txn, remote.getId(), local);
			IntroductionSucceededEvent event =
					new IntroductionSucceededEvent(contact);
			txn.attach(event);

			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} finally {
			db.endTransaction(txn);
		}
	}
	@Override
	public Collection<PendingContact> getPendingContacts() throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			BdfList list = getPendingContacts(txn);
			List<PendingContact> contacts = new ArrayList<>(list.size());
			for (Object o : list) {
				BdfDictionary d = (BdfDictionary) o;
				contacts.add(new PendingContact(d.getString("name"),
						d.getLong("timestamp")));
			}
			db.commitTransaction(txn);
			return contacts;
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} finally {
			db.endTransaction(txn);
		}
	}
	private BdfList getPendingContacts(Transaction txn)
			throws DbException, FormatException {
		Group localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID,
				MAJOR_VERSION);
		BdfDictionary d = clientHelper
				.getGroupMetadataAsDictionary(txn, localGroup.getId());
		return d.getList(PENDING_CONTACTS, new BdfList());
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
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
		PrivateMessageHeader header = new PrivateMessageHeader(
				m.getId(), groupId, timestamp, local, read, false, false);
		ContactId contactId = getContactId(txn, groupId);
		PrivateMessageReceivedEvent<PrivateMessageHeader> event =
				new PrivateMessageReceivedEvent<>(header, contactId);
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
	public Collection<PrivateMessageHeader> getMessageHeaders(Transaction txn,
			ContactId c) throws DbException {
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
		Collection<PrivateMessageHeader> headers = new ArrayList<>();
		for (MessageStatus s : statuses) {
			MessageId id = s.getMessageId();
			BdfDictionary meta = metadata.get(id);
			if (meta == null) continue;
			try {
				long timestamp = meta.getLong("timestamp");
				boolean local = meta.getBoolean("local");
				boolean read = meta.getBoolean("read");
				headers.add(new PrivateMessageHeader(id, g, timestamp, local,
						read, s.isSent(), s.isSeen()));
			} catch (FormatException e) {
				throw new DbException(e);
			}
		}
		return headers;
	}

	@Override
	public String getMessageBody(MessageId m) throws DbException {
		try {
			// 0: private message body
			return clientHelper.getMessageAsList(m).getString(0);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

}
