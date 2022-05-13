package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.ContactHook;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxPropertiesUpdate;
import org.briarproject.bramble.api.mailbox.MailboxPropertiesUpdateMailbox;
import org.briarproject.bramble.api.mailbox.MailboxPropertyManager;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager.MailboxHook;
import org.briarproject.bramble.api.mailbox.MailboxVersion;
import org.briarproject.bramble.api.mailbox.RemoteMailboxPropertiesUpdateEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.validation.IncomingMessageHook;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.ACCEPT_DO_NOT_SHARE;
import static org.briarproject.bramble.mailbox.MailboxApi.CLIENT_SUPPORTS;

@NotNullByDefault
class MailboxPropertyManagerImpl implements MailboxPropertyManager,
		OpenDatabaseHook, ContactHook, ClientVersioningHook,
		IncomingMessageHook, MailboxHook {

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final ClientVersioningManager clientVersioningManager;
	private final MetadataParser metadataParser;
	private final ContactGroupFactory contactGroupFactory;
	private final Clock clock;
	private final MailboxSettingsManager mailboxSettingsManager;
	private final CryptoComponent crypto;
	private final Group localGroup;

	@Inject
	MailboxPropertyManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			MetadataParser metadataParser,
			ContactGroupFactory contactGroupFactory, Clock clock,
			MailboxSettingsManager mailboxSettingsManager,
			CryptoComponent crypto) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.clientVersioningManager = clientVersioningManager;
		this.metadataParser = metadataParser;
		this.contactGroupFactory = contactGroupFactory;
		this.clock = clock;
		this.mailboxSettingsManager = mailboxSettingsManager;
		this.crypto = crypto;
		localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID,
				MAJOR_VERSION);
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		if (db.containsGroup(txn, localGroup.getId())) {
			return;
		}
		db.addGroup(txn, localGroup);
		// Set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) {
			addingContact(txn, c);
		}
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		db.addGroup(txn, g);
		// Apply the client's visibility to the contact group
		Visibility client = clientVersioningManager
				.getClientVisibility(txn, c.getId(), CLIENT_ID, MAJOR_VERSION);
		db.setGroupVisibility(txn, c.getId(), g.getId(), client);
		// Attach the contact ID to the group
		clientHelper.setContactId(txn, g.getId(), c.getId());
		MailboxProperties ownProps =
				mailboxSettingsManager.getOwnMailboxProperties(txn);
		if (ownProps != null) {
			// We are paired, create and send props to the newly added contact
			createAndSendProperties(txn, c, ownProps.getServerSupports(),
					ownProps.getOnion());
		} else {
			// Not paired, but we still want to get our clientSupports sent
			sendEmptyProperties(txn, c);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public void mailboxPaired(Transaction txn, String ownOnion,
			List<MailboxVersion> serverSupports)
			throws DbException {
		for (Contact c : db.getContacts(txn)) {
			createAndSendProperties(txn, c, serverSupports, ownOnion);
		}
	}

	@Override
	public void mailboxUnpaired(Transaction txn) throws DbException {
		for (Contact c : db.getContacts(txn)) {
			sendEmptyProperties(txn, c);
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
	public DeliveryAction incomingMessage(Transaction txn, Message m,
			Metadata meta) throws DbException, InvalidMessageException {
		try {
			BdfDictionary d = metadataParser.parse(meta);
			// Get latest non-local update in the same group (from same contact)
			LatestUpdate latest = findLatest(txn, m.getGroupId(), false);
			if (latest != null) {
				if (d.getLong(MSG_KEY_VERSION) > latest.version) {
					db.deleteMessage(txn, latest.messageId);
					db.deleteMessageMetadata(txn, latest.messageId);
				} else {
					// Delete this update, we already have a newer one
					db.deleteMessage(txn, m.getId());
					db.deleteMessageMetadata(txn, m.getId());
					return ACCEPT_DO_NOT_SHARE;
				}
			}
			ContactId c = clientHelper.getContactId(txn, m.getGroupId());
			BdfList body = clientHelper.getMessageAsList(txn, m.getId());
			MailboxPropertiesUpdate p = parseProperties(body);
			txn.attach(new RemoteMailboxPropertiesUpdateEvent(c, p));
			// Reset message retransmission timers for the contact. Avoiding
			// messages getting stranded:
			// - on our mailbox, if they now have a mailbox but didn't before
			// - on the contact's old mailbox, if they removed their mailbox
			// - on the contact's old mailbox, if they replaced their mailbox
			db.resetUnackedMessagesToSend(txn, c);
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
		return ACCEPT_DO_NOT_SHARE;
	}

	@Override
	@Nullable
	public MailboxPropertiesUpdate getLocalProperties(Transaction txn,
			ContactId c) throws DbException {
		return getProperties(txn, db.getContact(txn, c), true);
	}

	@Override
	@Nullable
	public MailboxPropertiesUpdate getRemoteProperties(Transaction txn,
			ContactId c) throws DbException {
		return getProperties(txn, db.getContact(txn, c), false);
	}

	/**
	 * Creates and sends an update message to the given contact. The message
	 * holds our own mailbox's onion, generated unique properties, and lists of
	 * supported Mailbox API version(s). All of which the contact needs to
	 * communicate with our Mailbox.
	 */
	private void createAndSendProperties(Transaction txn, Contact c,
			List<MailboxVersion> serverSupports, String ownOnion)
			throws DbException {
		MailboxPropertiesUpdate p = new MailboxPropertiesUpdateMailbox(
				CLIENT_SUPPORTS, serverSupports, ownOnion,
				new MailboxAuthToken(crypto.generateUniqueId().getBytes()),
				new MailboxFolderId(crypto.generateUniqueId().getBytes()),
				new MailboxFolderId(crypto.generateUniqueId().getBytes()));
		Group g = getContactGroup(c);
		storeMessageReplaceLatest(txn, g.getId(), p);
	}

	/**
	 * Sends an update message with empty properties to the given contact. The
	 * empty update indicates for the receiving contact that we don't have any
	 * Mailbox that they can use. It still includes the list of Mailbox API
	 * version(s) that we support as a client.
	 */
	private void sendEmptyProperties(Transaction txn, Contact c)
			throws DbException {
		Group g = getContactGroup(c);
		MailboxPropertiesUpdate p =
				new MailboxPropertiesUpdate(CLIENT_SUPPORTS);
		storeMessageReplaceLatest(txn, g.getId(), p);
	}

	@Nullable
	private MailboxPropertiesUpdate getProperties(Transaction txn,
			Contact c, boolean local) throws DbException {
		MailboxPropertiesUpdate p = null;
		Group g = getContactGroup(c);
		try {
			LatestUpdate latest = findLatest(txn, g.getId(), local);
			if (latest != null) {
				BdfList body =
						clientHelper.getMessageAsList(txn, latest.messageId);
				p = parseProperties(body);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
		return p;
	}

	private void storeMessageReplaceLatest(Transaction txn, GroupId g,
			MailboxPropertiesUpdate p) throws DbException {
		try {
			LatestUpdate latest = findLatest(txn, g, true);
			long version = latest == null ? 1 : latest.version + 1;
			Message m = clientHelper.createMessage(g, clock.currentTimeMillis(),
					encodeProperties(version, p));
			BdfDictionary meta = new BdfDictionary();
			meta.put(MSG_KEY_VERSION, version);
			meta.put(MSG_KEY_LOCAL, true);
			clientHelper.addLocalMessage(txn, m, meta, true, false);
			if (latest != null) {
				db.removeMessage(txn, latest.messageId);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Nullable
	private LatestUpdate findLatest(Transaction txn, GroupId g, boolean local)
			throws DbException, FormatException {
		Map<MessageId, BdfDictionary> metadata =
				clientHelper.getMessageMetadataAsDictionary(txn, g);
		// We should have at most 1 local and 1 remote
		if (metadata.size() > 2) {
			throw new IllegalStateException();
		}
		for (Entry<MessageId, BdfDictionary> e : metadata.entrySet()) {
			BdfDictionary meta = e.getValue();
			if (meta.getBoolean(MSG_KEY_LOCAL) == local) {
				return new LatestUpdate(e.getKey(),
						meta.getLong(MSG_KEY_VERSION));
			}
		}
		return null;
	}

	private BdfList encodeProperties(long version, MailboxPropertiesUpdate p) {
		BdfDictionary dict = new BdfDictionary();
		BdfList serverSupports = new BdfList();
		if (p.hasMailbox()) {
			MailboxPropertiesUpdateMailbox pm =
					(MailboxPropertiesUpdateMailbox) p;
			serverSupports = encodeSupportsList(pm.getServerSupports());
			dict.put(PROP_KEY_ONION, pm.getOnion());
			dict.put(PROP_KEY_AUTHTOKEN, pm.getAuthToken().getBytes());
			dict.put(PROP_KEY_INBOXID, pm.getInboxId().getBytes());
			dict.put(PROP_KEY_OUTBOXID, pm.getOutboxId().getBytes());
		}
		return BdfList.of(version, encodeSupportsList(p.getClientSupports()),
				serverSupports, dict);
	}

	private BdfList encodeSupportsList(List<MailboxVersion> supportsList) {
		BdfList supports = new BdfList();
		for (MailboxVersion version : supportsList) {
			supports.add(BdfList.of(version.getMajor(), version.getMinor()));
		}
		return supports;
	}

	private MailboxPropertiesUpdate parseProperties(BdfList body)
			throws FormatException {
		BdfList clientSupports = body.getList(1);
		BdfList serverSupports = body.getList(2);
		BdfDictionary dict = body.getDictionary(3);
		return clientHelper.parseAndValidateMailboxPropertiesUpdate(
				clientSupports, serverSupports, dict
		);
	}

	private Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID, MAJOR_VERSION,
				c);
	}

	private static class LatestUpdate {

		private final MessageId messageId;
		private final long version;

		private LatestUpdate(MessageId messageId, long version) {
			this.messageId = messageId;
			this.version = version;
		}
	}
}
