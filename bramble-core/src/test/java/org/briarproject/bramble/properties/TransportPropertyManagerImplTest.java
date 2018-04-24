package org.briarproject.bramble.properties;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.properties.TransportPropertyManager.CLIENT_ID;
import static org.briarproject.bramble.api.properties.TransportPropertyManager.MAJOR_VERSION;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TransportPropertyManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final ClientVersioningManager clientVersioningManager =
			context.mock(ClientVersioningManager.class);
	private final MetadataParser metadataParser =
			context.mock(MetadataParser.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);
	private final Clock clock = context.mock(Clock.class);

	private final Group localGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final BdfDictionary fooPropertiesDict = BdfDictionary.of(
			new BdfEntry("fooKey1", "fooValue1"),
			new BdfEntry("fooKey2", "fooValue2")
	);
	private final BdfDictionary barPropertiesDict = BdfDictionary.of(
			new BdfEntry("barKey1", "barValue1"),
			new BdfEntry("barKey2", "barValue2")
	);
	private final TransportProperties fooProperties, barProperties;

	private int nextContactId = 0;

	public TransportPropertyManagerImplTest() throws Exception {
		fooProperties = new TransportProperties();
		for (String key : fooPropertiesDict.keySet())
			fooProperties.put(key, fooPropertiesDict.getString(key));
		barProperties = new TransportProperties();
		for (String key : barPropertiesDict.keySet())
			barProperties.put(key, barPropertiesDict.getString(key));
	}

	private TransportPropertyManagerImpl createInstance() {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createLocalGroup(CLIENT_ID,
					MAJOR_VERSION);
			will(returnValue(localGroup));
		}});
		return new TransportPropertyManagerImpl(db, clientHelper,
				clientVersioningManager, metadataParser, contactGroupFactory,
				clock);
	}

	@Test
	public void testCreatesGroupsAtStartup() throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact(true);
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
		}});
		// Copy the latest local properties into the group
		expectGetLocalProperties(txn);
		expectStoreMessage(txn, contactGroup.getId(), "foo", fooPropertiesDict,
				1, true, true);
		expectStoreMessage(txn, contactGroup.getId(), "bar", barPropertiesDict,
				1, true, true);

		TransportPropertyManagerImpl t = createInstance();
		t.createLocalState(txn);
	}

	@Test
	public void testDoesNotCreateGroupsAtStartupIfAlreadyCreated()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(true));
		}});

		TransportPropertyManagerImpl t = createInstance();
		t.createLocalState(txn);
	}

	@Test
	public void testCreatesContactGroupWhenAddingContact() throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact(true);
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
		}});
		// Copy the latest local properties into the group
		expectGetLocalProperties(txn);
		expectStoreMessage(txn, contactGroup.getId(), "foo", fooPropertiesDict,
				1, true, true);
		expectStoreMessage(txn, contactGroup.getId(), "bar", barPropertiesDict,
				1, true, true);

		TransportPropertyManagerImpl t = createInstance();
		t.addingContact(txn, contact);
	}

	@Test
	public void testRemovesGroupWhenRemovingContact() throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact(true);
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);

		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).removeGroup(txn, contactGroup);
		}});

		TransportPropertyManagerImpl t = createInstance();
		t.removingContact(txn, contact);
	}

	@Test
	public void testDoesNotDeleteAnythingWhenFirstUpdateIsDelivered()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		GroupId contactGroupId = new GroupId(getRandomId());
		long timestamp = 123456789;
		Message message = getMessage(contactGroupId, timestamp);
		Metadata meta = new Metadata();
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 2),
				new BdfEntry("local", false)
		);
		Map<MessageId, BdfDictionary> messageMetadata =
				new LinkedHashMap<>();
		// A remote update for another transport should be ignored
		MessageId barUpdateId = new MessageId(getRandomId());
		messageMetadata.put(barUpdateId, BdfDictionary.of(
				new BdfEntry("transportId", "bar"),
				new BdfEntry("version", 1),
				new BdfEntry("local", false)
		));
		// A local update for the same transport should be ignored
		MessageId localUpdateId = new MessageId(getRandomId());
		messageMetadata.put(localUpdateId, BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 1),
				new BdfEntry("local", true)
		));

		context.checking(new Expectations() {{
			oneOf(metadataParser).parse(meta);
			will(returnValue(metaDictionary));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroupId);
			will(returnValue(messageMetadata));
		}});

		TransportPropertyManagerImpl t = createInstance();
		assertFalse(t.incomingMessage(txn, message, meta));
	}

	@Test
	public void testDeletesOlderUpdatesWhenUpdateIsDelivered()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		GroupId contactGroupId = new GroupId(getRandomId());
		long timestamp = 123456789;
		Message message = getMessage(contactGroupId, timestamp);
		Metadata meta = new Metadata();
		// Version 4 is being delivered
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 4),
				new BdfEntry("local", false)
		);
		Map<MessageId, BdfDictionary> messageMetadata =
				new LinkedHashMap<>();
		// An older remote update for the same transport should be deleted
		MessageId fooVersion3 = new MessageId(getRandomId());
		messageMetadata.put(fooVersion3, BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 3),
				new BdfEntry("local", false)
		));

		context.checking(new Expectations() {{
			oneOf(metadataParser).parse(meta);
			will(returnValue(metaDictionary));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroupId);
			will(returnValue(messageMetadata));
			// The previous update (version 3) should be deleted
			oneOf(db).deleteMessage(txn, fooVersion3);
			oneOf(db).deleteMessageMetadata(txn, fooVersion3);
		}});

		TransportPropertyManagerImpl t = createInstance();
		assertFalse(t.incomingMessage(txn, message, meta));
	}

	@Test
	public void testDeletesObsoleteUpdateWhenDelivered() throws Exception {
		Transaction txn = new Transaction(null, false);
		GroupId contactGroupId = new GroupId(getRandomId());
		long timestamp = 123456789;
		Message message = getMessage(contactGroupId, timestamp);
		Metadata meta = new Metadata();
		// Version 3 is being delivered
		BdfDictionary metaDictionary = BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 3),
				new BdfEntry("local", false)
		);
		Map<MessageId, BdfDictionary> messageMetadata =
				new LinkedHashMap<>();
		// A newer remote update for the same transport should not be deleted
		MessageId fooVersion4 = new MessageId(getRandomId());
		messageMetadata.put(fooVersion4, BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 4),
				new BdfEntry("local", false)
		));

		context.checking(new Expectations() {{
			oneOf(metadataParser).parse(meta);
			will(returnValue(metaDictionary));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroupId);
			will(returnValue(messageMetadata));
			// The update being delivered (version 3) should be deleted
			oneOf(db).deleteMessage(txn, message.getId());
			oneOf(db).deleteMessageMetadata(txn, message.getId());
		}});

		TransportPropertyManagerImpl t = createInstance();
		assertFalse(t.incomingMessage(txn, message, meta));
	}

	@Test
	public void testStoresRemotePropertiesWithVersion0() throws Exception {
		Contact contact = getContact(true);
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		Transaction txn = new Transaction(null, false);
		Map<TransportId, TransportProperties> properties =
				new LinkedHashMap<>();
		properties.put(new TransportId("foo"), fooProperties);
		properties.put(new TransportId("bar"), barProperties);

		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
		}});
		expectStoreMessage(txn, contactGroup.getId(), "foo", fooPropertiesDict,
				0, false, false);
		expectStoreMessage(txn, contactGroup.getId(), "bar", barPropertiesDict,
				0, false, false);

		TransportPropertyManagerImpl t = createInstance();
		t.addRemoteProperties(txn, contact.getId(), properties);
	}

	@Test
	public void testReturnsLatestLocalProperties() throws Exception {
		Transaction txn = new Transaction(null, true);

		expectGetLocalProperties(txn);

		TransportPropertyManagerImpl t = createInstance();
		Map<TransportId, TransportProperties> local = t.getLocalProperties(txn);
		assertEquals(2, local.size());
		assertEquals(fooProperties, local.get(new TransportId("foo")));
		assertEquals(barProperties, local.get(new TransportId("bar")));
	}

	@Test
	public void testReturnsEmptyPropertiesIfNoLocalPropertiesAreFound()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		Map<MessageId, BdfDictionary> messageMetadata =
				new LinkedHashMap<>();
		// A local update for another transport should be ignored
		MessageId barUpdateId = new MessageId(getRandomId());
		messageMetadata.put(barUpdateId, BdfDictionary.of(
				new BdfEntry("transportId", "bar"),
				new BdfEntry("version", 1),
				new BdfEntry("local", true)
		));

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					localGroup.getId());
			will(returnValue(messageMetadata));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		TransportPropertyManagerImpl t = createInstance();
		assertEquals(0, t.getLocalProperties(new TransportId("foo")).size());
	}

	@Test
	public void testReturnsLocalProperties() throws Exception {
		Transaction txn = new Transaction(null, true);
		Map<MessageId, BdfDictionary> messageMetadata =
				new LinkedHashMap<>();
		// A local update for another transport should be ignored
		MessageId barUpdateId = new MessageId(getRandomId());
		messageMetadata.put(barUpdateId, BdfDictionary.of(
				new BdfEntry("transportId", "bar"),
				new BdfEntry("version", 1),
				new BdfEntry("local", true)
		));
		// A local update for the right transport should be returned
		MessageId fooUpdateId = new MessageId(getRandomId());
		messageMetadata.put(fooUpdateId, BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 1),
				new BdfEntry("local", true)
		));
		BdfList fooUpdate = BdfList.of("foo", 1, fooPropertiesDict);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					localGroup.getId());
			will(returnValue(messageMetadata));
			oneOf(clientHelper).getMessageAsList(txn, fooUpdateId);
			will(returnValue(fooUpdate));
			oneOf(clientHelper).parseAndValidateTransportProperties(
					fooPropertiesDict);
			will(returnValue(fooProperties));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		TransportPropertyManagerImpl t = createInstance();
		assertEquals(fooProperties,
				t.getLocalProperties(new TransportId("foo")));
	}

	@Test
	public void testReturnsRemotePropertiesOrEmptyProperties()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		Contact contact1 = getContact(false);
		Contact contact2 = getContact(true);
		Contact contact3 = getContact(true);
		List<Contact> contacts =
				Arrays.asList(contact1, contact2, contact3);
		Group contactGroup2 = getGroup(CLIENT_ID, MAJOR_VERSION);
		Group contactGroup3 = getGroup(CLIENT_ID, MAJOR_VERSION);
		Map<MessageId, BdfDictionary> messageMetadata3 =
				new LinkedHashMap<>();
		// A remote update for another transport should be ignored
		MessageId barUpdateId = new MessageId(getRandomId());
		messageMetadata3.put(barUpdateId, BdfDictionary.of(
				new BdfEntry("transportId", "bar"),
				new BdfEntry("version", 1),
				new BdfEntry("local", false)
		));
		// A local update for the right transport should be ignored
		MessageId localUpdateId = new MessageId(getRandomId());
		messageMetadata3.put(localUpdateId, BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 1),
				new BdfEntry("local", true)
		));
		// A remote update for the right transport should be returned
		MessageId fooUpdateId = new MessageId(getRandomId());
		messageMetadata3.put(fooUpdateId, BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 1),
				new BdfEntry("local", false)
		));
		BdfList fooUpdate = BdfList.of("foo", 1, fooPropertiesDict);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			// First contact: skipped because not active
			// Second contact: no updates
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact2);
			will(returnValue(contactGroup2));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup2.getId());
			will(returnValue(Collections.emptyMap()));
			// Third contact: returns an update
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact3);
			will(returnValue(contactGroup3));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup3.getId());
			will(returnValue(messageMetadata3));
			oneOf(clientHelper).getMessageAsList(txn, fooUpdateId);
			will(returnValue(fooUpdate));
			oneOf(clientHelper).parseAndValidateTransportProperties(
					fooPropertiesDict);
			will(returnValue(fooProperties));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		TransportPropertyManagerImpl t = createInstance();
		Map<ContactId, TransportProperties> properties =
				t.getRemoteProperties(new TransportId("foo"));
		assertEquals(3, properties.size());
		assertEquals(0, properties.get(contact1.getId()).size());
		assertEquals(0, properties.get(contact2.getId()).size());
		assertEquals(fooProperties, properties.get(contact3.getId()));
	}

	@Test
	public void testMergingUnchangedPropertiesDoesNotCreateUpdate()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		MessageId updateId = new MessageId(getRandomId());
		Map<MessageId, BdfDictionary> messageMetadata =
				Collections.singletonMap(updateId, BdfDictionary.of(
						new BdfEntry("transportId", "foo"),
						new BdfEntry("version", 1),
						new BdfEntry("local", true)
				));
		BdfList update = BdfList.of("foo", 1, fooPropertiesDict);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Merge the new properties with the existing properties
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					localGroup.getId());
			will(returnValue(messageMetadata));
			oneOf(clientHelper).getMessageAsList(txn, updateId);
			will(returnValue(update));
			oneOf(clientHelper).parseAndValidateTransportProperties(
					fooPropertiesDict);
			will(returnValue(fooProperties));
			// Properties are unchanged so we're done
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		TransportPropertyManagerImpl t = createInstance();
		t.mergeLocalProperties(new TransportId("foo"), fooProperties);
	}

	@Test
	public void testMergingNewPropertiesCreatesUpdate() throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact(true);
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// There are no existing properties to merge with
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					localGroup.getId());
			will(returnValue(Collections.emptyMap()));
			// Store the new properties in the local group, version 1
			expectStoreMessage(txn, localGroup.getId(), "foo",
					fooPropertiesDict, 1, true, false);
			// Store the new properties in each contact's group, version 1
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(Collections.emptyMap()));
			expectStoreMessage(txn, contactGroup.getId(), "foo",
					fooPropertiesDict, 1, true, true);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		TransportPropertyManagerImpl t = createInstance();
		t.mergeLocalProperties(new TransportId("foo"), fooProperties);
	}

	@Test
	public void testMergingUpdatedPropertiesCreatesUpdate() throws Exception {
		Transaction txn = new Transaction(null, false);
		Contact contact = getContact(true);
		Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
		BdfDictionary oldMetadata = BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 1),
				new BdfEntry("local", true)
		);
		MessageId localGroupUpdateId = new MessageId(getRandomId());
		Map<MessageId, BdfDictionary> localGroupMessageMetadata =
				Collections.singletonMap(localGroupUpdateId, oldMetadata);
		MessageId contactGroupUpdateId = new MessageId(getRandomId());
		Map<MessageId, BdfDictionary> contactGroupMessageMetadata =
				Collections.singletonMap(contactGroupUpdateId, oldMetadata);
		TransportProperties oldProperties = new TransportProperties();
		oldProperties.put("fooKey1", "oldFooValue1");
		BdfDictionary oldPropertiesDict = BdfDictionary.of(
				new BdfEntry("fooKey1", "oldFooValue1")
		);
		BdfList oldUpdate = BdfList.of("foo", 1, oldPropertiesDict);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Merge the new properties with the existing properties
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					localGroup.getId());
			will(returnValue(localGroupMessageMetadata));
			oneOf(clientHelper).getMessageAsList(txn, localGroupUpdateId);
			will(returnValue(oldUpdate));
			oneOf(clientHelper).parseAndValidateTransportProperties(
					oldPropertiesDict);
			will(returnValue(oldProperties));
			// Store the merged properties in the local group, version 2
			expectStoreMessage(txn, localGroup.getId(), "foo",
					fooPropertiesDict, 2, true, false);
			// Delete the previous update
			oneOf(db).removeMessage(txn, localGroupUpdateId);
			// Store the merged properties in each contact's group, version 2
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(contactGroupMessageMetadata));
			expectStoreMessage(txn, contactGroup.getId(), "foo",
					fooPropertiesDict, 2, true, true);
			// Delete the previous update
			oneOf(db).removeMessage(txn, contactGroupUpdateId);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		TransportPropertyManagerImpl t = createInstance();
		t.mergeLocalProperties(new TransportId("foo"), fooProperties);
	}

	private Contact getContact(boolean active) {
		ContactId c = new ContactId(nextContactId++);
		return new Contact(c, getAuthor(), localAuthor.getId(),
				true, active);
	}

	private Message getMessage(GroupId g, long timestamp) {
		MessageId messageId = new MessageId(getRandomId());
		byte[] raw = getRandomBytes(MAX_MESSAGE_BODY_LENGTH);
		return new Message(messageId, g, timestamp, raw);
	}

	private void expectGetLocalProperties(Transaction txn) throws Exception {
		Map<MessageId, BdfDictionary> messageMetadata = new LinkedHashMap<>();
		// The latest update for transport "foo" should be returned
		MessageId fooVersion999 = new MessageId(getRandomId());
		messageMetadata.put(fooVersion999, BdfDictionary.of(
				new BdfEntry("transportId", "foo"),
				new BdfEntry("version", 999)
		));
		// The latest update for transport "bar" should be returned
		MessageId barVersion3 = new MessageId(getRandomId());
		messageMetadata.put(barVersion3, BdfDictionary.of(
				new BdfEntry("transportId", "bar"),
				new BdfEntry("version", 3)
		));
		BdfList fooUpdate = BdfList.of("foo", 999, fooPropertiesDict);
		BdfList barUpdate = BdfList.of("bar", 3, barPropertiesDict);

		context.checking(new Expectations() {{
			// Find the latest local update for each transport
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					localGroup.getId());
			will(returnValue(messageMetadata));
			// Retrieve and parse the latest local properties
			oneOf(clientHelper).getMessageAsList(txn, fooVersion999);
			will(returnValue(fooUpdate));
			oneOf(clientHelper).parseAndValidateTransportProperties(
					fooPropertiesDict);
			will(returnValue(fooProperties));
			oneOf(clientHelper).getMessageAsList(txn, barVersion3);
			will(returnValue(barUpdate));
			oneOf(clientHelper).parseAndValidateTransportProperties(
					barPropertiesDict);
			will(returnValue(barProperties));
		}});
	}

	private void expectStoreMessage(Transaction txn, GroupId g,
			String transportId, BdfDictionary properties, long version,
			boolean local, boolean shared) throws Exception {
		long timestamp = 123456789;
		BdfList body = BdfList.of(transportId, version, properties);
		Message message = getMessage(g, timestamp);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry("transportId", transportId),
				new BdfEntry("version", version),
				new BdfEntry("local", local)
		);

		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
			oneOf(clientHelper).createMessage(g, timestamp, body);
			will(returnValue(message));
			oneOf(clientHelper).addLocalMessage(txn, message, meta, shared);
		}});
	}
}
