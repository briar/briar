package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Pair;
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
import org.briarproject.bramble.api.identity.Author;
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
import org.briarproject.briar.api.remotewipe.MessageEncoder;
import org.briarproject.briar.api.remotewipe.MessageParser;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;
import org.briarproject.briar.api.remotewipe.RemoteWipeMessageHeader;
import org.briarproject.briar.api.remotewipe.RemoteWipeReceivedEvent;
import org.briarproject.briar.api.socialbackup.ShardReceivedEvent;
import org.briarproject.briar.client.ConversationClientImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
import static org.briarproject.briar.remotewipe.MessageType.SETUP;
import static org.briarproject.briar.remotewipe.MessageType.WIPE;
import static org.briarproject.briar.remotewipe.RemoteWipeConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.remotewipe.RemoteWipeConstants.GROUP_KEY_RECEIVED_WIPE;
import static org.briarproject.briar.remotewipe.RemoteWipeConstants.GROUP_KEY_WIPERS;
import static org.briarproject.briar.remotewipe.RemoteWipeConstants.MAX_MESSAGE_AGE;
import static org.briarproject.briar.remotewipe.RemoteWipeConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.remotewipe.RemoteWipeConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.briar.remotewipe.RemoteWipeConstants.MSG_KEY_TIMESTAMP;
import static org.briarproject.briar.remotewipe.RemoteWipeConstants.THRESHOLD;

public class RemoteWipeManagerImpl extends ConversationClientImpl
		implements RemoteWipeManager, ContactManager.ContactHook,
		ClientVersioningManager.ClientVersioningHook,
		LifecycleManager.OpenDatabaseHook {

	private final ClientVersioningManager clientVersioningManager;
	private final Group localGroup;
	private final Clock clock;
	private final ContactGroupFactory contactGroupFactory;
	private final ContactManager contactManager;
	private final MessageEncoder messageEncoder;
	private final MessageParser messageParser;
	private RemoteWipeManager.Observer observer;

	@Inject
	protected RemoteWipeManagerImpl(
			DatabaseComponent db,
			ClientHelper clientHelper,
			MetadataParser metadataParser,
			MessageTracker messageTracker,
			Clock clock,
			MessageEncoder messageEncoder,
			MessageParser messageParser,
			ContactManager contactManager,
			ClientVersioningManager clientVersioningManager,
			ContactGroupFactory contactGroupFactory) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.clock = clock;
		this.contactGroupFactory = contactGroupFactory;
		this.contactManager = contactManager;
		this.clientVersioningManager = clientVersioningManager;
		this.messageEncoder = messageEncoder;
		this.messageParser = messageParser;
		localGroup =
				contactGroupFactory.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

	@Override
	public void listenForPanic(RemoteWipeManager.Observer observer) {
		this.observer = observer;
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		if (db.containsGroup(txn, localGroup.getId())) return;
		db.addGroup(txn, localGroup);
		// Set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
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
		System.out.println("Incoming remote wipe message");
		MessageType type = MessageType.fromValue(body.getLong(0).intValue());
		if (type == SETUP) {
			messageTracker.trackIncomingMessage(txn, m);
			ContactId contactId = getContactId(txn, m.getGroupId());

			MessageStatus status = db.getMessageStatus(txn, contactId,
					m.getId());
			txn.attach(new RemoteWipeReceivedEvent(
					createMessageHeader(m, meta, status), contactId));
		} else if (type == WIPE) {
			if (!remoteWipeIsSetup(txn)) return false;
			if (clock.currentTimeMillis() - m.getTimestamp() > MAX_MESSAGE_AGE)
				return false;

			ContactId contactId = getContactId(txn, m.getGroupId());
			// Check if contact is in list of wipers
			if (findMessage(txn, m.getGroupId(), SETUP, true) != null) {
				System.out.println("Got a valid wipe message from a wiper");

				BdfDictionary existingMeta =
						clientHelper.getGroupMetadataAsDictionary(txn,
								localGroup.getId());
				BdfList receivedWipeMessages =
						existingMeta.getOptionalList(GROUP_KEY_RECEIVED_WIPE);

				if (receivedWipeMessages == null)
					receivedWipeMessages = new BdfList();

				// Traverse the list backwards to avoid problems when removing items
				for (int i = receivedWipeMessages.size() - 1; i >= 0; --i) {
					BdfList receivedWipeMessage =
							receivedWipeMessages.getList(i);

					long timestamp = receivedWipeMessage.getLong(1);
					System.out.println("Message age: " +
							(clock.currentTimeMillis() - timestamp));
					// Filter the messages for old ones
					if (clock.currentTimeMillis() - timestamp >
							MAX_MESSAGE_AGE) {
						System.out.println("Removing outdated wipe message");
						receivedWipeMessages.remove(i);
					} else if (receivedWipeMessage.getLong(0).intValue() ==
							contactId.getInt()) {
						// If we already have one from this contact, ignore
						System.out.println(
								"Duplicate wipe message received - ignoring");
						return false;
					}
				}

				if (receivedWipeMessages.size() + 1 == THRESHOLD) {
					System.out.println("Threshold reached - panic!");
					if (observer != null) {
						observer.onPanic();
					}
					// we could here clear the metadata to allow us to send
					// the wipe messages several times when testing
				} else {
					BdfList newReceivedWipeMessage = new BdfList();
					newReceivedWipeMessage.add(contactId.getInt());
					newReceivedWipeMessage.add(m.getTimestamp());
					receivedWipeMessages.add(newReceivedWipeMessage);
					BdfDictionary newMeta = new BdfDictionary();
					newMeta.put(GROUP_KEY_RECEIVED_WIPE, receivedWipeMessages);
					clientHelper.mergeGroupMetadata(txn, localGroup.getId(),
							newMeta);
				}
			}
		}
		return false;
	}

	public void setup(Transaction txn, List<ContactId> wipers)
			throws DbException, FormatException {
		// If we already have a set of wipers do nothing
		// TODO revoke existing wipers who are not present in the new list
		if (remoteWipeIsSetup(txn)) throw new FormatException();

		if (wipers.size() < 2) throw new FormatException();

		BdfList wipersMetadata = new BdfList();

		for (ContactId c : wipers) {
			Contact contact = contactManager.getContact(txn, c);
			System.out.println("Sending a setup message...");
			sendSetupMessage(txn, contact);
			wipersMetadata.add(clientHelper.toList(contact.getAuthor()));
		}

		System.out.println("All setup messages sent");

		// Make a record of this locally
		if (!db.containsGroup(txn, localGroup.getId()))
			db.addGroup(txn, localGroup);

		BdfDictionary meta = new BdfDictionary();
		meta.put(GROUP_KEY_WIPERS, wipersMetadata);

		if (!db.containsGroup(txn, localGroup.getId()))
			db.addGroup(txn, localGroup);
		clientHelper.mergeGroupMetadata(txn, localGroup.getId(), meta);
	}

	private void sendSetupMessage(Transaction txn, Contact contact)
			throws DbException, FormatException {
		Group group = getContactGroup(contact);
		GroupId g = group.getId();
		if (!db.containsGroup(txn, g)) db.addGroup(txn, group);
		long timestamp = clock.currentTimeMillis();

		byte[] body = messageEncoder.encodeSetupMessage();

		Message m = clientHelper.createMessage(g, timestamp, body);
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
		// Check that we have a SETUP message from contact
		if (!amWiper(txn, contact.getId())) throw new DbException();

		Group group = getContactGroup(contact);
		GroupId g = group.getId();
		if (!db.containsGroup(txn, g)) db.addGroup(txn, group);

		long timestamp = clock.currentTimeMillis();

		byte[] body = messageEncoder.encodeWipeMessage();

		Message m = clientHelper.createMessage(g, timestamp, body);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_TIMESTAMP, timestamp),
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, WIPE.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, true)
		);
		clientHelper.addLocalMessage(txn, m, meta, true, false);
		messageTracker.trackOutgoingMessage(txn, m);
	}

	public boolean amWiper(Transaction txn, ContactId contactId) {
		try {
			GroupId groupId =
					getContactGroup(db.getContact(txn, contactId)).getId();
			return findMessage(txn, groupId, SETUP, false) != null;
		} catch (DbException e) {
			return false;
		} catch (FormatException e) {
			return false;
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
							createMessageHeader(message, meta, status));
				} else if (meta.getLong(MSG_KEY_MESSAGE_TYPE).intValue() ==
						WIPE.getValue()) {
					Message message = clientHelper
							.getMessage(txn, messageEntry.getKey());
					MessageStatus status = db.getMessageStatus(txn, contactId,
							messageEntry.getKey());
					headers.add(
							createMessageHeader(message, meta, status));
				}
			}
			return headers;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private RemoteWipeMessageHeader createMessageHeader(
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

	@Override
	public void onClientVisibilityChanging(Transaction txn, Contact c,
			Group.Visibility v) throws DbException {
		// Apply the client's visibility to the contact group
		Group g = getContactGroup(c);
		db.setGroupVisibility(txn, c.getId(), g.getId(), v);
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
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
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
//		if (results.size() > 1) throw new DbException();
		if (results.isEmpty()) return null;
		Map.Entry<MessageId, BdfDictionary> e =
				results.entrySet().iterator().next();
		return new Pair<>(e.getKey(), e.getValue());
	}

	@Override
	public boolean remoteWipeIsSetup(Transaction txn) {
		try {
			return !db.getGroupMetadata(txn, localGroup.getId()).isEmpty();
		} catch (DbException e) {
			return false;
		}
	}

	public List<Author> getWipers(Transaction txn) throws DbException {
		try {
			BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn,
					localGroup.getId());
			BdfList bdfWipers = meta.getList(GROUP_KEY_WIPERS);

			List<Author> wipers = new ArrayList<>(bdfWipers.size());
			for (int i = 0; i < bdfWipers.size(); i++) {
				BdfList author = bdfWipers.getList(i);
				wipers.add(clientHelper.parseAndValidateAuthor(author));
			}
			return wipers;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}
}
