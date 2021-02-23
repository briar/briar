package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfIncomingMessageHook;
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
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
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
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.briar.api.socialbackup.BackupExistsException;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.Shard;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.Collections.singletonMap;
import static org.briarproject.briar.socialbackup.MessageType.BACKUP;
import static org.briarproject.briar.socialbackup.MessageType.SHARD;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_VERSION;

@NotNullByDefault
class SocialBackupManagerImpl extends BdfIncomingMessageHook
		implements SocialBackupManager, OpenDatabaseHook, ContactHook,
		ClientVersioningHook {

	private final ClientVersioningManager clientVersioningManager;
	private final TransportPropertyManager transportPropertyManager;
	private final ContactGroupFactory contactGroupFactory;
	private final BackupMetadataParser backupMetadataParser;
	private final BackupMetadataEncoder backupMetadataEncoder;
	private final BackupPayloadEncoder backupPayloadEncoder;
	private final MessageEncoder messageEncoder;
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
			MessageEncoder messageEncoder,
			IdentityManager identityManager,
			ContactManager contactManager,
			CryptoComponent crypto,
			DarkCrystal darkCrystal,
			Clock clock) {
		super(db, clientHelper, metadataParser);
		this.clientVersioningManager = clientVersioningManager;
		this.transportPropertyManager = transportPropertyManager;
		this.contactGroupFactory = contactGroupFactory;
		this.backupMetadataParser = backupMetadataParser;
		this.backupMetadataEncoder = backupMetadataEncoder;
		this.backupPayloadEncoder = backupPayloadEncoder;
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
		// TODO: Add the contact to our backup, if any
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
		// TODO: Remove the contact from our backup, if any
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
			// TODO: Add the shard to our backup, if any
		} else if (type == BACKUP) {
			// Keep the newest version of the backup, delete any older versions
			int version = meta.getLong(MSG_KEY_VERSION).intValue();
			BdfDictionary query = BdfDictionary.of(
					new BdfEntry(MSG_KEY_MESSAGE_TYPE, BACKUP.getValue()),
					new BdfEntry(MSG_KEY_LOCAL, false));
			Map<MessageId, BdfDictionary> results =
					clientHelper.getMessageMetadataAsDictionary(txn,
							m.getGroupId(), query);
			if (results.size() > 1) throw new DbException();
			for (Entry<MessageId, BdfDictionary> e : results.entrySet()) {
				MessageId prevId = e.getKey();
				BdfDictionary prevMeta = e.getValue();
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
		if (getBackupMetadata(txn) != null) throw new BackupExistsException();
		// Load the contacts
		List<Contact> custodians = new ArrayList<>(custodianIds.size());
		for (ContactId custodianId : custodianIds) {
			custodians.add(contactManager.getContact(txn, custodianId));
		}
		// Create the encrypted backup payload
		SecretKey secret = crypto.generateSecretKey();
		BackupPayload payload = createBackupPayload(txn, secret, 0);
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
			clientHelper.mergeGroupMetadata(txn, localGroup.getId(), meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, c);
	}

	private BackupPayload createBackupPayload(Transaction txn,
			SecretKey secret, int version) throws DbException {
		Identity identity = identityManager.getIdentity(txn);
		Collection<Contact> contacts = contactManager.getContacts(txn);
		List<Contact> eligible = new ArrayList<>();
		List<Map<TransportId, TransportProperties>> properties =
				new ArrayList<>();
		// Include all contacts whose handshake public keys we know
		for (Contact c : contacts) {
			if (c.getHandshakePublicKey() != null) {
				eligible.add(c);
				properties.add(getTransportProperties(txn, c.getId()));
				// TODO: Include shard received from contact, if any
			}
		}
		return backupPayloadEncoder.encodeBackupPayload(secret, identity,
				eligible, properties, version);
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
		GroupId g = getContactGroup(custodian).getId();
		long timestamp = clock.currentTimeMillis();
		byte[] body = messageEncoder.encodeShardMessage(shard);
		Message m = clientHelper.createMessage(g, timestamp, body);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, SHARD.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, true));
		clientHelper.addLocalMessage(txn, m, meta, true, false);
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
				new BdfEntry(MSG_KEY_VERSION, version));
		clientHelper.addLocalMessage(txn, m, meta, true, false);
	}
}
