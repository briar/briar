package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.attachment.AttachmentHeader;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.DeletionResult;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;
import org.briarproject.briar.api.remotewipe.RemoteWipeMessageHeader;
import org.briarproject.briar.client.ConversationClientImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
import static org.briarproject.briar.remotewipe.MessageType.SETUP;
import static org.briarproject.briar.remotewipe.MessageType.WIPE;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_TIMESTAMP;

public class RemoteWipeManagerImpl extends ConversationClientImpl
		implements RemoteWipeManager, LifecycleManager.OpenDatabaseHook {

	private final ClientVersioningManager clientVersioningManager;
	private final Group localGroup;
	private final Clock clock;
	private final ContactGroupFactory contactGroupFactory;
	private final ContactManager contactManager;

	@Inject
	protected RemoteWipeManagerImpl(
			DatabaseComponent db,
			ClientHelper clientHelper,
			MetadataParser metadataParser,
			MessageTracker messageTracker,
			Clock clock,
			ContactManager contactManager,
			ClientVersioningManager clientVersioningManager,
			ContactGroupFactory contactGroupFactory) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.clock = clock;
		this.contactGroupFactory = contactGroupFactory;
		this.contactManager = contactManager;
		this.clientVersioningManager = clientVersioningManager;
		localGroup =
				contactGroupFactory.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		System.out.println("DATAbase opened");
		if (db.containsGroup(txn, localGroup.getId())) return;
		db.addGroup(txn, localGroup);
		// Set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) {
			// Create a group to share with the contact
			Group g = getContactGroup(c);
			db.addGroup(txn, g);
			// Apply the client's visibility to the contact group
			Group.Visibility client = clientVersioningManager.getClientVisibility(txn,
					c.getId(), CLIENT_ID, MAJOR_VERSION);
			db.setGroupVisibility(txn, c.getId(), g.getId(), client);
			// Attach the contact ID to the group
			setContactId(txn, g.getId(), c.getId());
		}
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
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {
		System.out.println("Incoming message called");
		MessageType type = MessageType.fromValue(body.getLong(0).intValue());
		if (type == SETUP) {


			messageTracker.trackIncomingMessage(txn, m);
//					message.getGroupId turn into contactid
//		txn.attach event
		} else if (type == WIPE) {

			ContactId contactId = getContactId(txn, m.getGroupId());
			// check if contact is in list of wipers
			// if so, increment counter
			// check if counter = threshold
		}
		return false;
	}

	public void setup(Transaction txn, List<ContactId> wipers)
			throws DbException, FormatException {
        // TODO if (we already have a set of wipers?) do something
        if (wipers.size() < 2) throw new FormatException();
		for (ContactId c : wipers) {
			System.out.println("Sending a setup message...");
			sendSetupMessage(txn, contactManager.getContact(txn, c));
		}

		System.out.println("All setup messages sent");
		if (!db.containsGroup(txn, localGroup.getId()))
			db.addGroup(txn, localGroup);
		// TODO Make some sort of record of this
        // clientHelper.mergeGroupMetadata(txn, localGroup.getId(), meta)
	}

	private void sendSetupMessage(Transaction txn, Contact contact)
			throws DbException, FormatException {
		Group group = getContactGroup(contact);
		GroupId g = group.getId();
		if (!db.containsGroup(txn, g)) db.addGroup(txn, group);
		long timestamp = clock.currentTimeMillis();

		byte[] body = "setup message".getBytes(); // TODO

		Message m = clientHelper.createMessage(g, timestamp, body);
		// TODO remote-wipe versions of MESSAGE_KEY
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, SETUP.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, true),
				new BdfEntry(MSG_KEY_TIMESTAMP, timestamp)
		);
		clientHelper.addLocalMessage(txn, m, meta, true, false);
		messageTracker.trackOutgoingMessage(txn, m);
	}


	public void wipe(Transaction txn, Contact contact)
			throws DbException, FormatException {
		// TODO check that we have a SETUP message from contact
		Group group = getContactGroup(contact);
		GroupId g = group.getId();
		if (!db.containsGroup(txn, g)) db.addGroup(txn, group);
		long timestamp = clock.currentTimeMillis();

		byte[] body = "wipe message".getBytes(); // TODO

		Message m = clientHelper.createMessage(g, timestamp, body);
		// TODO remote-wipe versions of MESSAGE_KEY
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, SETUP.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, true),
				new BdfEntry(MSG_KEY_TIMESTAMP, timestamp)
		);
		clientHelper.addLocalMessage(txn, m, meta, true, false);
		messageTracker.trackOutgoingMessage(txn, m);
	}


	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, c);
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
			for (Map.Entry<MessageId, BdfDictionary> messageEntry : messages
					.entrySet()) {
				BdfDictionary meta = messageEntry.getValue();
				if (meta.getLong(MSG_KEY_MESSAGE_TYPE).intValue() ==
						SETUP.getValue()) {
					Message message = clientHelper
							.getMessage(txn, messageEntry.getKey());
					MessageStatus status = db.getMessageStatus(txn, contactId,
							messageEntry.getKey());
					headers.add(
							createSetupMessageHeader(message, meta, status));
				}
			}
			return headers;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private RemoteWipeMessageHeader createSetupMessageHeader(
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
		return new RemoteWipeMessageHeader(
				message.getId(), message.getGroupId(), timestamp,
				isLocal, read, status.isSent(), status.isSeen(),
				attachmentHeaders);
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
		DeletionResult result = new DeletionResult();
		return result;
	}

	@Override
	public DeletionResult deleteMessages(Transaction txn, ContactId c,
			Set<MessageId> messageIds) throws DbException {
		DeletionResult result = new DeletionResult();
		return result;
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
}
