package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.ContactManager.ContactHook;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.briar.api.attachment.AttachmentHeader;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.DeletionResult;
import org.briarproject.briar.api.socialbackup.BackupExistsException;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.DarkCrystal;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.Shard;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.ShardMessageHeader;
import org.briarproject.briar.api.socialbackup.ShardReceivedEvent;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;
import org.briarproject.briar.client.ConversationClientImpl;
import org.briarproject.briar.api.socialbackup.ContactData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
import static org.briarproject.briar.socialbackup.MessageType.BACKUP;
import static org.briarproject.briar.socialbackup.MessageType.SHARD;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_VERSION;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_TIMESTAMP;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_VERSION;

@NotNullByDefault
class SocialBackupManagerImpl extends ConversationClientImpl
		implements SocialBackupManager, OpenDatabaseHook, ContactHook,
		ClientVersioningHook {

	private final ClientVersioningManager clientVersioningManager;
	private final TransportPropertyManager transportPropertyManager;
	private final ContactGroupFactory contactGroupFactory;
	private final BackupMetadataParser backupMetadataParser;
	private final BackupMetadataEncoder backupMetadataEncoder;
	private final BackupPayloadEncoder backupPayloadEncoder;
	private final org.briarproject.briar.api.socialbackup.MessageParser
			messageParser;
	private final org.briarproject.briar.api.socialbackup.MessageEncoder
			messageEncoder;
	private final IdentityManager identityManager;
	private final ContactManager contactManager;
	private final CryptoComponent crypto;
	private final DarkCrystal darkCrystal;
	private final Clock clock;
	private final Group localGroup;

	@Inject
	SocialBackupManagerImpl(
			DatabaseComponent db,
			ClientHelper clientHelper,
			MetadataParser metadataParser,
			ClientVersioningManager clientVersioningManager,
			TransportPropertyManager transportPropertyManager,
			ContactGroupFactory contactGroupFactory,
			BackupMetadataParser backupMetadataParser,
			BackupMetadataEncoder backupMetadataEncoder,
			BackupPayloadEncoder backupPayloadEncoder,
			org.briarproject.briar.api.socialbackup.MessageParser messageParser,
			org.briarproject.briar.api.socialbackup.MessageEncoder messageEncoder,
			IdentityManager identityManager,
			ContactManager contactManager,
			CryptoComponent crypto,
			DarkCrystal darkCrystal,
			Clock clock,
			MessageTracker messageTracker,
			ConversationManager conversationManager
	) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.clientVersioningManager = clientVersioningManager;
		this.transportPropertyManager = transportPropertyManager;
		this.contactGroupFactory = contactGroupFactory;
		this.backupMetadataParser = backupMetadataParser;
		this.backupMetadataEncoder = backupMetadataEncoder;
		this.backupPayloadEncoder = backupPayloadEncoder;
		this.messageParser = messageParser;
		this.messageEncoder = messageEncoder;
		this.identityManager = identityManager;
		this.contactManager = contactManager;
		this.crypto = crypto;
		this.darkCrystal = darkCrystal;
		localGroup =
				contactGroupFactory.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
		this.clock = clock;
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
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
		setContactId(txn, g.getId(), c.getId());
		// Add the contact to our backup, if any
		if (localBackupExists(txn)) {
			updateBackup(txn, loadContactData(txn));
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
		// Remove the contact from our backup, if any
		if (localBackupExists(txn)) {
			updateBackup(txn, loadContactData(txn));
		}
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
		MessageType type = MessageType.fromValue(body.getLong(0).intValue());
		if (type == SHARD) {
			// Only one shard should be received from each contact
			if (findMessage(txn, m.getGroupId(), SHARD, false) != null) {
				throw new FormatException();
			}
			ContactId contactId = getContactId(txn, m.getGroupId());
			// Add the shard to our backup, if any
			if (localBackupExists(txn)) {
				Shard shard = messageParser.parseShardMessage(body);
				List<ContactData> contactData = loadContactData(txn);
				ListIterator<ContactData> it = contactData.listIterator();
				while (it.hasNext()) {
					ContactData cd = it.next();
					if (cd.getContact().getId().equals(contactId)) {
						it.set(new ContactData(cd.getContact(),
								cd.getProperties(), shard));
						updateBackup(txn, contactData);
						break;
					}
				}
			}
			messageTracker.trackIncomingMessage(txn, m);

			MessageStatus status = db.getMessageStatus(txn, contactId,
					m.getId());
			txn.attach(new ShardReceivedEvent(
					createShardMessageHeader(m, meta, status), contactId));
		} else if (type == BACKUP) {
			// Keep the newest version of the backup, delete any older versions
			int version = meta.getLong(MSG_KEY_VERSION).intValue();
			Pair<MessageId, BdfDictionary> prev =
					findMessage(txn, m.getGroupId(), BACKUP, false);
			if (prev != null) {
				MessageId prevId = prev.getFirst();
				BdfDictionary prevMeta = prev.getSecond();
				int prevVersion = prevMeta.getLong(MSG_KEY_VERSION).intValue();
				if (version > prevVersion) {
					// This backup is newer - delete the previous backup
					db.deleteMessage(txn, prevId);
					db.deleteMessageMetadata(txn, prevId);
				} else {
					// We've already received a newer backup - delete this one
					db.deleteMessage(txn, m.getId());
					db.deleteMessageMetadata(txn, m.getId());
				}
			}
		}
		return false;
	}

	public ReturnShardPayload getReturnShardPayload(Transaction txn, ContactId contactId) throws DbException {
		GroupId groupId = getContactGroup(db.getContact(txn, contactId)).getId();
		return new ReturnShardPayload(getRemoteShard(txn, groupId), getRemoteBackup(txn, groupId));
	}

	public byte[] getReturnShardPayloadBytes(Transaction txn, ContactId contactId) throws DbException {
		return messageEncoder.encodeReturnShardPayload(getReturnShardPayload(txn, contactId));
	}

	public boolean amCustodian(Transaction txn, ContactId contactId) {
		try {
			GroupId groupId = getContactGroup(db.getContact(txn, contactId)).getId();
			return findMessage(txn, groupId, SHARD, false) != null;
		} catch (DbException e) {
			return false;
		} catch (FormatException e) {
			return false;
		}
	}

	@Nullable
	@Override
	public BackupMetadata getBackupMetadata(Transaction txn)
			throws DbException {
		try {
			BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn,
					localGroup.getId());

			return backupMetadataParser.parseBackupMetadata(meta);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void createBackup(Transaction txn, List<ContactId> custodianIds,
			int threshold) throws DbException {
		if (localBackupExists(txn)) throw new BackupExistsException();
		// Load the contacts
		List<Contact> custodians = new ArrayList<>(custodianIds.size());
		for (ContactId custodianId : custodianIds) {
			custodians.add(contactManager.getContact(txn, custodianId));
		}
		// Create the encrypted backup payload
		SecretKey secret = crypto.generateSecretKey();
		List<org.briarproject.briar.api.socialbackup.ContactData> contactData = loadContactData(txn);
		BackupPayload payload =
				createBackupPayload(txn, secret, contactData, 0);
		// Create the shards
		List<Shard> shards = darkCrystal.createShards(secret,
				custodians.size(), threshold);
		try {
			// Send the shard and backup messages to the custodians
			for (int i = 0; i < custodians.size(); i++) {
				Contact custodian = custodians.get(i);
				Shard shard = shards.get(i);
				sendShardMessage(txn, custodian, shard);
				sendBackupMessage(txn, custodian, 0, payload);
			}
			// Store the backup metadata
			List<Author> authors = new ArrayList<>(custodians.size());
			for (Contact custodian : custodians) {
				authors.add(custodian.getAuthor());
			}
			BackupMetadata backupMetadata =
					new BackupMetadata(secret, authors, threshold, 0);
			BdfDictionary meta =
					backupMetadataEncoder.encodeBackupMetadata(backupMetadata);

			if (!db.containsGroup(txn, localGroup.getId()))
				db.addGroup(txn, localGroup);
			clientHelper.mergeGroupMetadata(txn, localGroup.getId(), meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, c);
	}

	private ShardMessageHeader createShardMessageHeader(
			Message message, BdfDictionary meta, MessageStatus status
	)
			throws FormatException {

		boolean isLocal = meta.getBoolean(MSG_KEY_LOCAL);
		boolean read = meta.getBoolean(MSG_KEY_READ, false);
		long timestamp;
		if (isLocal) {
			timestamp = meta.getLong(MSG_KEY_TIMESTAMP);
		} else {
			timestamp = message.getTimestamp();
		}
		List<AttachmentHeader> attachmentHeaders =
				new ArrayList<>();
		return new ShardMessageHeader(
				message.getId(), message.getGroupId(), timestamp,
				isLocal, read, status.isSent(), status.isSeen(),
				attachmentHeaders);
	}

	@Override
	public Collection<ConversationMessageHeader> getMessageHeaders(
			Transaction txn, ContactId contactId) throws DbException {
		try {
			Contact contact = db.getContact(txn, contactId);
			GroupId contactGroupId = getContactGroup(contact).getId();
			Map<MessageId, BdfDictionary> messages = clientHelper
					.getMessageMetadataAsDictionary(txn, contactGroupId);
			List<ConversationMessageHeader> headers =
					new ArrayList<>();
			for (Entry<MessageId, BdfDictionary> messageEntry : messages
					.entrySet()) {
				BdfDictionary meta = messageEntry.getValue();
				if (meta.getLong(MSG_KEY_MESSAGE_TYPE).intValue() ==
						SHARD.getValue()) {
					Message message = clientHelper
							.getMessage(txn, messageEntry.getKey());
					MessageStatus status = db.getMessageStatus(txn, contactId,
							messageEntry.getKey());
					headers.add(createShardMessageHeader(message, meta, status));
				}
			}
			return headers;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Set<MessageId> getMessageIds(Transaction txn, ContactId contactId)
			throws DbException {
		Contact contact = db.getContact(txn, contactId);
		GroupId contactGroupId = getContactGroup(contact).getId();
		try {
			Map<MessageId, BdfDictionary> messages = clientHelper
					.getMessageMetadataAsDictionary(txn, contactGroupId);
			return messages.keySet();
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public DeletionResult deleteAllMessages(Transaction txn, ContactId c)
			throws DbException {
		return null;
	}

	@Override
	public DeletionResult deleteMessages(Transaction txn, ContactId c,
			Set<MessageId> messageIds) throws DbException {
		DeletionResult result = new DeletionResult();
		return result;
	}

	private void setContactId(Transaction txn, GroupId g, ContactId c)
			throws DbException {
		BdfDictionary d = new BdfDictionary();
		d.put(GROUP_KEY_CONTACT_ID, c.getInt());
		try {
			clientHelper.mergeGroupMetadata(txn, g, d);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private ContactId getContactId(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			return new ContactId(meta.getLong(GROUP_KEY_CONTACT_ID).intValue());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private BackupPayload createBackupPayload(Transaction txn,
			SecretKey secret, List<org.briarproject.briar.api.socialbackup.ContactData> contactData, int version)
			throws DbException {
		Identity identity = identityManager.getIdentity(txn);
		return backupPayloadEncoder.encodeBackupPayload(secret, identity,
				contactData, version);
	}

	private List<org.briarproject.briar.api.socialbackup.ContactData> loadContactData(Transaction txn)
			throws DbException {
		Collection<Contact> contacts = contactManager.getContacts(txn);
		List<org.briarproject.briar.api.socialbackup.ContactData> contactData = new ArrayList<>();
		for (Contact c : contacts) {
			// Skip contacts that are in the process of being removed
			Group contactGroup = getContactGroup(c);
			if (!db.containsGroup(txn, contactGroup.getId())) continue;
			Map<TransportId, TransportProperties> props =
					getTransportProperties(txn, c.getId());
			Shard shard = getRemoteShard(txn, contactGroup.getId());
			contactData.add(new org.briarproject.briar.api.socialbackup.ContactData(c, props, shard));
		}
		return contactData;
	}

	private Map<TransportId, TransportProperties> getTransportProperties(
			Transaction txn, ContactId c) throws DbException {
		// TODO: Include filtered properties for other transports
		TransportProperties p = transportPropertyManager
				.getRemoteProperties(txn, c, TorConstants.ID);
		return singletonMap(TorConstants.ID, p);
	}

	private void sendShardMessage(Transaction txn, Contact custodian,
			Shard shard) throws DbException, FormatException {
		Group group = getContactGroup(custodian);
		GroupId g = group.getId();
		if (!db.containsGroup(txn, g)) db.addGroup(txn, group);
		long timestamp = clock.currentTimeMillis();
		byte[] body = messageEncoder.encodeShardMessage(shard);
		Message m = clientHelper.createMessage(g, timestamp, body);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, SHARD.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, true),
				new BdfEntry(MSG_KEY_TIMESTAMP, timestamp)
		);
		clientHelper.addLocalMessage(txn, m, meta, true, false);
		messageTracker.trackOutgoingMessage(txn, m);
	}

	private void sendBackupMessage(Transaction txn, Contact custodian,
			int version, BackupPayload payload)
			throws DbException, FormatException {
		GroupId g = getContactGroup(custodian).getId();
		long timestamp = clock.currentTimeMillis();
		byte[] body = messageEncoder.encodeBackupMessage(version, payload);
		Message m = clientHelper.createMessage(g, timestamp, body);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, BACKUP.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, true),
				new BdfEntry(MSG_KEY_TIMESTAMP, timestamp),
				new BdfEntry(MSG_KEY_VERSION, version));
		clientHelper.addLocalMessage(txn, m, meta, true, false);
	}

	private boolean localBackupExists(Transaction txn) {
		try {
			return !db.getGroupMetadata(txn, localGroup.getId()).isEmpty();
		} catch (DbException e) {
			return false;
		}
	}

	@Nullable
	private Shard getRemoteShard(Transaction txn, GroupId g)
			throws DbException {
		try {
			Pair<MessageId, BdfDictionary> prev =
					findMessage(txn, g, SHARD, false);
			if (prev == null) return null;
			BdfList body = clientHelper.getMessageAsList(txn, prev.getFirst());
			return messageParser.parseShardMessage(body);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Nullable
	private BackupPayload getRemoteBackup(Transaction txn, GroupId g)
			throws DbException {
		try {
			Pair<MessageId, BdfDictionary> prev =
					findMessage(txn, g, BACKUP, false);
			if (prev == null) return null;
			BdfList body = clientHelper.getMessageAsList(txn, prev.getFirst());
			return messageParser.parseBackupMessage(body);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}
	private void updateBackup(Transaction txn, List<org.briarproject.briar.api.socialbackup.ContactData> contactData)
			throws DbException {
		BackupMetadata backupMetadata = requireNonNull(getBackupMetadata(txn));
		int newVersion = backupMetadata.getVersion() + 1;
		BackupPayload payload = createBackupPayload(txn,
				backupMetadata.getSecret(), contactData, newVersion);
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		try {
			for (Author author : backupMetadata.getCustodians()) {
				try {
					Contact custodian = contactManager.getContact(txn,
							author.getId(), localAuthor.getId());
					Group contactGroup = getContactGroup(custodian);
					Pair<MessageId, BdfDictionary> prev = findMessage(txn,
							contactGroup.getId(), BACKUP, true);
					if (prev != null) {
						// Delete the previous backup message
						MessageId prevId = prev.getFirst();
						db.deleteMessage(txn, prevId);
						db.deleteMessageMetadata(txn, prevId);
					}
					sendBackupMessage(txn, custodian, newVersion, payload);
				} catch (NoSuchContactException e) {
					// The custodian is no longer a contact - continue
				}
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
		BdfDictionary meta =
				BdfDictionary.of(new BdfEntry(GROUP_KEY_VERSION, newVersion));
		try {
			clientHelper.mergeGroupMetadata(txn, localGroup.getId(), meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Nullable
	private Pair<MessageId, BdfDictionary> findMessage(Transaction txn,
			GroupId g, MessageType type, boolean local)
			throws DbException, FormatException {
		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, type.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, local));
		Map<MessageId, BdfDictionary> results =
				clientHelper.getMessageMetadataAsDictionary(txn, g, query);
		if (results.size() > 1) throw new DbException();
		if (results.isEmpty()) return null;
		Entry<MessageId, BdfDictionary> e =
				results.entrySet().iterator().next();
		return new Pair<>(e.getKey(), e.getValue());
	}
}
