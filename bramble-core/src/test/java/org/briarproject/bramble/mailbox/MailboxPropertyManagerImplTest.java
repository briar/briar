package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxPropertiesUpdate;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.RemoteMailboxPropertiesUpdateEvent;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.mailbox.MailboxPropertyManager.CLIENT_ID;
import static org.briarproject.bramble.api.mailbox.MailboxPropertyManager.MAJOR_VERSION;
import static org.briarproject.bramble.api.mailbox.MailboxPropertyManager.MSG_KEY_LOCAL;
import static org.briarproject.bramble.api.mailbox.MailboxPropertyManager.MSG_KEY_VERSION;
import static org.briarproject.bramble.api.mailbox.MailboxPropertyManager.PROP_KEY_AUTHTOKEN;
import static org.briarproject.bramble.api.mailbox.MailboxPropertyManager.PROP_KEY_INBOXID;
import static org.briarproject.bramble.api.mailbox.MailboxPropertyManager.PROP_KEY_ONION;
import static org.briarproject.bramble.api.mailbox.MailboxPropertyManager.PROP_KEY_OUTBOXID;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.ACCEPT_DO_NOT_SHARE;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.hasEvent;
import static org.briarproject.bramble.test.TestUtils.mailboxPropertiesUpdateEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MailboxPropertyManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final ClientVersioningManager clientVersioningManager =
			context.mock(ClientVersioningManager.class);
	private final MetadataParser metadataParser =
			context.mock(MetadataParser.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);
	private final Clock clock = context.mock(Clock.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final MailboxSettingsManager mailboxSettingsManager =
			context.mock(MailboxSettingsManager.class);
	private final ContactManager contactManager =
			context.mock(ContactManager.class);

	private final Group localGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final BdfDictionary propsDict;
	private final BdfDictionary emptyPropsDict = new BdfDictionary();
	private final MailboxPropertiesUpdate props;
	private final MailboxProperties ownProps;

	public MailboxPropertyManagerImplTest() {
		ownProps = new MailboxProperties("http://bar.onion",
				new MailboxAuthToken(getRandomId()), true);
		props = new MailboxPropertiesUpdate(ownProps.getOnion(),
				new MailboxAuthToken(getRandomId()),
				new MailboxFolderId(getRandomId()),
				new MailboxFolderId(getRandomId()));
		propsDict = new BdfDictionary();
		propsDict.put(PROP_KEY_ONION, props.getOnion());
		propsDict.put(PROP_KEY_AUTHTOKEN, props.getAuthToken().getBytes());
		propsDict.put(PROP_KEY_INBOXID, props.getInboxId().getBytes());
		propsDict.put(PROP_KEY_OUTBOXID, props.getOutboxId().getBytes());
	}

	private MailboxPropertyManagerImpl createInstance() {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createLocalGroup(CLIENT_ID,
					MAJOR_VERSION);
			will(returnValue(localGroup));
		}});
		return new MailboxPropertyManagerImpl(db, clientHelper,
				clientVersioningManager, metadataParser, contactGroupFactory,
				clock, mailboxSettingsManager, contactManager, crypto);
	}

	@Test
	public void testCreatesGroupsAtUnpairedStartup() throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);

		context.checking(new Expectations() {{
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, localGroup);
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(clientVersioningManager).getClientVisibility(txn,
					contact.getId(), CLIENT_ID, MAJOR_VERSION);
			will(returnValue(SHARED));
			oneOf(db).setGroupVisibility(txn, contact.getId(),
					contactGroup.getId(), SHARED);
			oneOf(clientHelper).setContactId(txn, contactGroup.getId(),
					contact.getId());
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(returnValue(null));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		t.onDatabaseOpened(txn);
	}

	@Test
	public void testCreatesGroupsAndCreatesAndSendsAtPairedStartup()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();

		context.checking(new Expectations() {{
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, localGroup);
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(clientVersioningManager).getClientVisibility(txn,
					contact.getId(), CLIENT_ID, MAJOR_VERSION);
			will(returnValue(SHARED));
			oneOf(db).setGroupVisibility(txn, contact.getId(),
					contactGroup.getId(), SHARED);
			oneOf(clientHelper).setContactId(txn, contactGroup.getId(),
					contact.getId());
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(returnValue(ownProps));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getAuthToken()));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getInboxId()));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getOutboxId()));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			expectStoreMessage(txn, contactGroup.getId(), propsDict, 1, true);
		}});

		MailboxPropertyManagerImpl t = createInstance();
		t.onDatabaseOpened(txn);
	}

	@Test
	public void testDoesNotCreateGroupsAtStartupIfAlreadyCreated()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(true));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		t.onDatabaseOpened(txn);
	}

	@Test
	public void testCreatesContactGroupWhenAddingContactUnpaired()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);

		context.checking(new Expectations() {{
			// Create the group and share it with the contact
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(clientVersioningManager).getClientVisibility(txn,
					contact.getId(), CLIENT_ID, MAJOR_VERSION);
			will(returnValue(SHARED));
			oneOf(db).setGroupVisibility(txn, contact.getId(),
					contactGroup.getId(), SHARED);
			oneOf(clientHelper).setContactId(txn, contactGroup.getId(),
					contact.getId());
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(returnValue(null));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		t.addingContact(txn, contact);
	}

	@Test
	public void testCreatesContactGroupAndCreatesAndSendsWhenAddingContactPaired()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();

		context.checking(new Expectations() {{
			// Create the group and share it with the contact
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(clientVersioningManager).getClientVisibility(txn,
					contact.getId(), CLIENT_ID, MAJOR_VERSION);
			will(returnValue(SHARED));
			oneOf(db).setGroupVisibility(txn, contact.getId(),
					contactGroup.getId(), SHARED);
			oneOf(clientHelper).setContactId(txn, contactGroup.getId(),
					contact.getId());
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(returnValue(ownProps));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getAuthToken()));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getInboxId()));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getOutboxId()));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			expectStoreMessage(txn, contactGroup.getId(), propsDict, 1, true);
		}});

		MailboxPropertyManagerImpl t = createInstance();
		t.addingContact(txn, contact);
	}

	@Test
	public void testRemovesGroupWhenRemovingContact() throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);

		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).removeGroup(txn, contactGroup);
		}});

		MailboxPropertyManagerImpl t = createInstance();
		t.removingContact(txn, contact);
	}

	@Test
	public void testDoesNotDeleteAnythingWhenFirstUpdateIsDelivered()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		GroupId contactGroupId = new GroupId(getRandomId());
		Message message = getMessage(contactGroupId);
		BdfList body = BdfList.of(1, propsDict);
		Metadata meta = new Metadata();
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, false)
		);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		// A local update should be ignored
		MessageId localUpdateId = new MessageId(getRandomId());
		messageMetadata.put(localUpdateId, BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, true)
		));

		context.checking(new Expectations() {{
			oneOf(metadataParser).parse(meta);
			will(returnValue(metaDictionary));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroupId);
			will(returnValue(messageMetadata));
			oneOf(clientHelper).getContactId(txn, contactGroupId);
			oneOf(clientHelper).getMessageAsList(txn, message.getId());
			will(returnValue(body));
			oneOf(clientHelper).parseAndValidateMailboxPropertiesUpdate(
					propsDict);
			will(returnValue(props));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		assertEquals(ACCEPT_DO_NOT_SHARE,
				t.incomingMessage(txn, message, meta));
		assertTrue(hasEvent(txn, RemoteMailboxPropertiesUpdateEvent.class));
	}

	@Test
	public void testDeletesOlderUpdateWhenUpdateIsDelivered()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		GroupId contactGroupId = new GroupId(getRandomId());
		Message message = getMessage(contactGroupId);
		BdfList body = BdfList.of(1, propsDict);
		Metadata meta = new Metadata();
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 2),
				new BdfEntry(MSG_KEY_LOCAL, false)
		);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		// This older version should be deleted
		MessageId updateId = new MessageId(getRandomId());
		messageMetadata.put(updateId, BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, false)
		));
		// A local update should be ignored
		MessageId localUpdateId = new MessageId(getRandomId());
		messageMetadata.put(localUpdateId, BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, true)
		));

		context.checking(new Expectations() {{
			oneOf(metadataParser).parse(meta);
			will(returnValue(metaDictionary));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroupId);
			will(returnValue(messageMetadata));
			oneOf(db).deleteMessage(txn, updateId);
			oneOf(db).deleteMessageMetadata(txn, updateId);
			oneOf(clientHelper).getContactId(txn, contactGroupId);
			oneOf(clientHelper).getMessageAsList(txn, message.getId());
			will(returnValue(body));
			oneOf(clientHelper).parseAndValidateMailboxPropertiesUpdate(
					propsDict);
			will(returnValue(props));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		assertEquals(ACCEPT_DO_NOT_SHARE,
				t.incomingMessage(txn, message, meta));
		assertTrue(hasEvent(txn, RemoteMailboxPropertiesUpdateEvent.class));
	}

	@Test
	public void testDeletesObsoleteUpdateWhenDelivered() throws Exception {
		Transaction txn = new Transaction(null, false);
		GroupId contactGroupId = new GroupId(getRandomId());
		Message message = getMessage(contactGroupId);
		Metadata meta = new Metadata();
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 3),
				new BdfEntry(MSG_KEY_LOCAL, false)
		);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		// This newer version should not be deleted
		MessageId updateId = new MessageId(getRandomId());
		messageMetadata.put(updateId, BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 4),
				new BdfEntry(MSG_KEY_LOCAL, false)
		));

		context.checking(new Expectations() {{
			oneOf(metadataParser).parse(meta);
			will(returnValue(metaDictionary));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroupId);
			will(returnValue(messageMetadata));
			oneOf(db).deleteMessage(txn, message.getId());
			oneOf(db).deleteMessageMetadata(txn, message.getId());
		}});

		MailboxPropertyManagerImpl t = createInstance();
		assertEquals(ACCEPT_DO_NOT_SHARE,
				t.incomingMessage(txn, message, meta));
		assertFalse(hasEvent(txn, RemoteMailboxPropertiesUpdateEvent.class));
	}

	@Test
	public void testCreatesAndStoresLocalPropertiesWithNewVersionOnPairing()
			throws Exception {
		Contact contact = getContact();
		List<Contact> contacts = singletonList(contact);
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);

		Transaction txn = new Transaction(null, false);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		MessageId latestId = new MessageId(getRandomId());
		messageMetadata.put(latestId, BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, true)
		));
		// Some remote props, ignored
		messageMetadata.put(new MessageId(getRandomId()), BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 3),
				new BdfEntry(MSG_KEY_LOCAL, false)
		));

		context.checking(new Expectations() {{
			oneOf(contactManager).getContacts();
			will(returnValue(contacts));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getAuthToken()));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getInboxId()));
			oneOf(crypto).generateUniqueId();
			will(returnValue(props.getOutboxId()));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			expectStoreMessage(txn, contactGroup.getId(), propsDict, 2, true);
			oneOf(db).removeMessage(txn, latestId);
		}});

		MailboxPropertyManagerImpl t = createInstance();
		t.mailboxPaired(txn, ownProps.getOnion());
	}

	@Test
	public void testStoresEmptyLocalPropertiesWithNewVersionOnUnpairing()
			throws Exception {
		Contact contact = getContact();
		List<Contact> contacts = singletonList(contact);
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);

		Transaction txn = new Transaction(null, false);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		MessageId latestId = new MessageId(getRandomId());
		messageMetadata.put(latestId, BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, true)
		));
		// Some remote props, ignored
		messageMetadata.put(new MessageId(getRandomId()), BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 3),
				new BdfEntry(MSG_KEY_LOCAL, false)
		));

		context.checking(new Expectations() {{
			oneOf(contactManager).getContacts();
			will(returnValue(contacts));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			expectStoreMessage(txn, contactGroup.getId(), emptyPropsDict,
					2, true);
			oneOf(db).removeMessage(txn, latestId);
		}});

		MailboxPropertyManagerImpl t = createInstance();
		t.mailboxUnpaired(txn);
	}

	@Test
	public void testGetRemoteProperties()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, false)
		);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		MessageId fooUpdateId = new MessageId(getRandomId());
		messageMetadata.put(fooUpdateId, metaDictionary);
		BdfList fooUpdate = BdfList.of(1, propsDict);

		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			oneOf(clientHelper).getMessageAsList(txn, fooUpdateId);
			will(returnValue(fooUpdate));
			oneOf(clientHelper).parseAndValidateMailboxPropertiesUpdate(
					propsDict);
			will(returnValue(props));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		MailboxPropertiesUpdate remote =
				t.getRemoteProperties(txn, contact.getId());
		assertTrue(mailboxPropertiesUpdateEqual(remote, props));
	}

	@Test
	public void testGetRemotePropertiesReturnsNullBecauseNoUpdate()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		Map<MessageId, BdfDictionary> emptyMessageMetadata =
				new LinkedHashMap<>();

		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(emptyMessageMetadata));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		assertNull(t.getRemoteProperties(txn, contact.getId()));
	}

	@Test
	public void testGetRemotePropertiesReturnsNullBecauseEmptyUpdate()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, false)
		);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		MessageId fooUpdateId = new MessageId(getRandomId());
		messageMetadata.put(fooUpdateId, metaDictionary);
		BdfList fooUpdate = BdfList.of(1, emptyPropsDict);

		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			oneOf(clientHelper).getMessageAsList(txn, fooUpdateId);
			will(returnValue(fooUpdate));
			oneOf(clientHelper).parseAndValidateMailboxPropertiesUpdate(
					emptyPropsDict);
			will(returnValue(null));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		assertNull(t.getRemoteProperties(txn, contact.getId()));
	}

	@Test
	public void testGetLocalProperties()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, true)
		);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		MessageId fooUpdateId = new MessageId(getRandomId());
		messageMetadata.put(fooUpdateId, metaDictionary);
		BdfList fooUpdate = BdfList.of(1, propsDict);

		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			oneOf(clientHelper).getMessageAsList(txn, fooUpdateId);
			will(returnValue(fooUpdate));
			oneOf(clientHelper).parseAndValidateMailboxPropertiesUpdate(
					propsDict);
			will(returnValue(props));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		MailboxPropertiesUpdate local =
				t.getLocalProperties(txn, contact.getId());
		assertTrue(mailboxPropertiesUpdateEqual(local, props));
	}

	@Test
	public void testGetLocalPropertiesReturnsNullBecauseNoUpdate()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		Map<MessageId, BdfDictionary> emptyMessageMetadata =
				new LinkedHashMap<>();

		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(emptyMessageMetadata));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		assertNull(t.getLocalProperties(txn, contact.getId()));
	}

	@Test
	public void testGetLocalPropertiesReturnsNullBecauseEmptyUpdate()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact();
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, 1),
				new BdfEntry(MSG_KEY_LOCAL, true)
		);
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		MessageId fooUpdateId = new MessageId(getRandomId());
		messageMetadata.put(fooUpdateId, metaDictionary);
		BdfList fooUpdate = BdfList.of(1, emptyPropsDict);

		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			oneOf(clientHelper).getMessageAsList(txn, fooUpdateId);
			will(returnValue(fooUpdate));
			oneOf(clientHelper).parseAndValidateMailboxPropertiesUpdate(
					emptyPropsDict);
			will(returnValue(null));
		}});

		MailboxPropertyManagerImpl t = createInstance();
		assertNull(t.getLocalProperties(txn, contact.getId()));
	}

	private void expectStoreMessage(Transaction txn, GroupId g,
			BdfDictionary properties, long version, boolean local)
			throws Exception {
		BdfList body = BdfList.of(version, properties);
		Message message = getMessage(g);
		long timestamp = message.getTimestamp();
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_VERSION, version),
				new BdfEntry(MSG_KEY_LOCAL, local)
		);

		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
			oneOf(clientHelper).createMessage(g, timestamp, body);
			will(returnValue(message));
			oneOf(clientHelper).addLocalMessage(txn, message, meta, true,
					false);
		}});
	}
}
