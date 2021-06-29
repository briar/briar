package org.briarproject.briar.handshakekeyexchange;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.DeletionResult;
import org.briarproject.briar.api.handshakekeyexchange.HandshakeKeyExchangeManager;
import org.briarproject.briar.client.ConversationClientImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.handshakekeyexchange.HandshakeKeyExchangeConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.handshakekeyexchange.HandshakeKeyExchangeConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.handshakekeyexchange.HandshakeKeyExchangeConstants.MSG_KEY_TIMESTAMP;

public class HandshakeKeyExchangeManagerImpl extends ConversationClientImpl
		implements
		HandshakeKeyExchangeManager, LifecycleManager.OpenDatabaseHook,
		ContactManager.ContactHook,
		ClientVersioningManager.ClientVersioningHook {

	private final ClientVersioningManager clientVersioningManager;
	private final ContactGroupFactory contactGroupFactory;
	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final Group localGroup;
	private final Clock clock;
	private PublicKey handshakePublicKey;
	private static final Logger LOG =
			getLogger(HandshakeKeyExchangeManager.class.getName());


	@Inject
	protected HandshakeKeyExchangeManagerImpl(
			DatabaseComponent db,
			ClientHelper clientHelper,
			MetadataParser metadataParser,
			MessageTracker messageTracker,
			ClientVersioningManager clientVersioningManager,
			ContactGroupFactory contactGroupFactory,
			ContactManager contactManager,
			IdentityManager identityManager,
			Clock clock
	) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.clientVersioningManager = clientVersioningManager;
		this.contactGroupFactory = contactGroupFactory;
		this.contactManager = contactManager;
		this.identityManager = identityManager;
		this.clock = clock;
		localGroup =
				contactGroupFactory.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		// Get our own handshake public key
		handshakePublicKey = identityManager.getHandshakeKeys(txn).getPublic();

		if (!db.containsGroup(txn, localGroup.getId())) {
			db.addGroup(txn, localGroup);

			// Set things up for any pre-existing contacts
			for (Contact c : db.getContacts(txn)) addingContact(txn, c);
		} else {
			for (Contact c : db.getContacts(txn)) {
				if (c.getHandshakePublicKey() == null) {
					sendHandshakePublicKey(txn, c);
				} else {
					LOG.info("Have pk for contact " + c.getAuthor().getName());
				}
			}
		}
	}

	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, c);
	}

	@Override
	public Collection<ConversationMessageHeader> getMessageHeaders(
			Transaction txn, ContactId contactId) throws DbException {
		return new ArrayList<>();
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
		GroupId g = getContactGroup(db.getContact(txn, c)).getId();
		for (MessageId messageId : db.getMessageIds(txn, g)) {
			db.deleteMessage(txn, messageId);
			db.deleteMessageMetadata(txn, messageId);
		}
		messageTracker.initializeGroupCount(txn, g);
		return new DeletionResult();
	}


	@Override
	public DeletionResult deleteMessages(Transaction txn, ContactId c,
			Set<MessageId> messageIds) throws DbException {
		for (MessageId m : messageIds) {
			db.deleteMessage(txn, m);
			db.deleteMessageMetadata(txn, m);
		}
		return new DeletionResult();
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {
		LOG.info("Incoming HandshakeKeyExchange message");
		ContactId contactId = getContactId(txn, m.getGroupId());
		Contact c = contactManager.getContact(txn, contactId);
		if (c.getHandshakePublicKey() != null) {
			LOG.info("Already have public key - ignoring message");
			return false;
		}
		LOG.info("Adding contact's handshake public key");
		PublicKey handshakePublicKey = new AgreementPublicKey(body.getRaw(0));
		contactManager
				.setHandshakePublicKey(txn, contactId, handshakePublicKey);
		return false;
	}

	private ContactId getContactId(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			return new ContactId(meta.getLong(
					HandshakeKeyExchangeConstants.GROUP_KEY_CONTACT_ID)
					.intValue());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		db.addGroup(txn, g);
		// Apply the client's visibility to the contact group
		Group.Visibility client =
				clientVersioningManager.getClientVisibility(txn,
						c.getId(), CLIENT_ID, MAJOR_VERSION);
		db.setGroupVisibility(txn, c.getId(), g.getId(), client);
		// Attach the contact ID to the group
		setContactId(txn, g.getId(), c.getId());

		if (c.getHandshakePublicKey() == null) {
			sendHandshakePublicKey(txn, c);
		} else {
			LOG.info("Have pk for contact " + c.getAuthor().getName());
		}
	}

	private void sendHandshakePublicKey(Transaction txn, Contact c)
			throws DbException {
		LOG.info("Sending our handshake public key to " + c.getAuthor().getName());
		Group group = getContactGroup(c);
		GroupId g = group.getId();
		if (!db.containsGroup(txn, g)) db.addGroup(txn, group);
		long timestamp = clock.currentTimeMillis();

		BdfList bodyList = new BdfList();
		bodyList.add(handshakePublicKey);
		try {
			byte[] body = clientHelper.toByteArray(bodyList);
			Message m = clientHelper.createMessage(g, timestamp, body);

			BdfDictionary meta = BdfDictionary.of(
					new BdfEntry(MSG_KEY_LOCAL, true),
					new BdfEntry(MSG_KEY_TIMESTAMP, timestamp)
			);
			clientHelper.addLocalMessage(txn, m, meta, true, false);
		} catch (FormatException e) {
			throw new DbException();
		}

//		messageTracker.trackOutgoingMessage(txn, m);
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
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

	@Override
	public void onClientVisibilityChanging(Transaction txn, Contact c,
			Group.Visibility v) throws DbException {
		// Apply the client's visibility to the contact group
		Group g = getContactGroup(c);
		db.setGroupVisibility(txn, c.getId(), g.getId(), v);
	}
}
