package org.briarproject.db;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseConfig;
import org.briarproject.TestUtils;
import org.briarproject.api.Settings;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.StorageStatus;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.transport.IncomingKeys;
import org.briarproject.api.transport.OutgoingKeys;
import org.briarproject.api.transport.TransportKeys;
import org.briarproject.system.SystemClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.api.db.Metadata.REMOVE;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class H2DatabaseTest extends BriarTestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	private static final int MAX_SIZE = 5 * ONE_MEGABYTE;

	private final File testDir = TestUtils.getTestDirectory();
	private final Random random = new Random();
	private final ClientId clientId;
	private final GroupId groupId;
	private final Group group;
	private final Author author;
	private final AuthorId localAuthorId;
	private final LocalAuthor localAuthor;
	private final MessageId messageId;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message;
	private final TransportId transportId;
	private final ContactId contactId;

	public H2DatabaseTest() throws Exception {
		clientId = new ClientId(TestUtils.getRandomId());
		groupId = new GroupId(TestUtils.getRandomId());
		byte[] descriptor = new byte[MAX_GROUP_DESCRIPTOR_LENGTH];
		group = new Group(groupId, clientId, descriptor);
		AuthorId authorId = new AuthorId(TestUtils.getRandomId());
		author = new Author(authorId, "Alice", new byte[MAX_PUBLIC_KEY_LENGTH]);
		localAuthorId = new AuthorId(TestUtils.getRandomId());
		timestamp = System.currentTimeMillis();
		localAuthor = new LocalAuthor(localAuthorId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[123], timestamp,
				StorageStatus.ACTIVE);
		messageId = new MessageId(TestUtils.getRandomId());
		size = 1234;
		raw = new byte[size];
		random.nextBytes(raw);
		message = new Message(messageId, groupId, timestamp, raw);
		transportId = new TransportId("id");
		contactId = new ContactId(1);
	}

	@Before
	public void setUp() {
		assertTrue(testDir.mkdirs());
	}

	@Test
	public void testPersistence() throws Exception {
		// Store some records
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsGroup(txn, groupId));
		db.addGroup(txn, group);
		assertTrue(db.containsGroup(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.addMessage(txn, message, true);
		assertTrue(db.containsMessage(txn, messageId));
		db.commitTransaction(txn);
		db.close();

		// Check that the records are still there
		db = open(true);
		txn = db.startTransaction();
		assertTrue(db.containsContact(txn, contactId));
		assertTrue(db.containsGroup(txn, groupId));
		assertTrue(db.containsMessage(txn, messageId));
		byte[] raw1 = db.getRawMessage(txn, messageId);
		assertArrayEquals(raw, raw1);
		// Delete the records
		db.removeMessage(txn, messageId);
		db.removeContact(txn, contactId);
		db.removeGroup(txn, groupId);
		db.commitTransaction(txn);
		db.close();

		// Check that the records are gone
		db = open(true);
		txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		assertFalse(db.containsGroup(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUnsubscribingRemovesMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, true);

		// Unsubscribing from the group should remove the message
		assertTrue(db.containsMessage(txn, messageId));
		db.removeGroup(txn, groupId);
		assertFalse(db.containsMessage(txn, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustHaveSeenFlagFalse() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);
		db.addMessage(txn, message, true);

		// The message has no status yet, so it should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertTrue(ids.isEmpty());

		// Adding a status with seen = false should make the message sendable
		db.addStatus(txn, contactId, messageId, false, false);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertFalse(ids.isEmpty());
		Iterator<MessageId> it = ids.iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// Changing the status to seen = true should make the message unsendable
		db.raiseSeenFlag(txn, contactId, messageId);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustBeSubscribed() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The contact is not subscribed, so the message should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertTrue(ids.isEmpty());

		// The contact subscribing should make the message sendable
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertFalse(ids.isEmpty());
		Iterator<MessageId> it = ids.iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// The contact unsubscribing should make the message unsendable
		db.setGroups(txn, contactId, Collections.<Group>emptyList(), 2);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustFitCapacity() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The message is sendable, but too large to send
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				size - 1);
		assertTrue(ids.isEmpty());

		// The message is just the right size to send
		ids = db.getMessagesToSend(txn, contactId, size);
		assertFalse(ids.isEmpty());
		Iterator<MessageId> it = ids.iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustBeVisible() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The subscription is not visible to the contact, so the message
		// should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertTrue(ids.isEmpty());

		// Making the subscription visible should make the message sendable
		db.addVisibility(txn, contactId, groupId);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertFalse(ids.isEmpty());
		Iterator<MessageId> it = ids.iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessagesToAck() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);

		// Add some messages to ack
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new Message(messageId1, groupId, timestamp, raw);
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, true);
		db.raiseAckFlag(txn, contactId, messageId);
		db.addMessage(txn, message1, true);
		db.addStatus(txn, contactId, messageId1, false, true);
		db.raiseAckFlag(txn, contactId, messageId1);

		// Both message IDs should be returned
		Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		assertEquals(ids, db.getMessagesToAck(txn, contactId, 1234));

		// Remove both message IDs
		db.lowerAckFlag(txn, contactId, Arrays.asList(messageId, messageId1));

		// Both message IDs should have been removed
		assertEquals(Collections.emptyList(), db.getMessagesToAck(txn,
				contactId, 1234));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testDuplicateMessageReceived() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);

		// Receive the same message twice
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, true);
		db.raiseAckFlag(txn, contactId, messageId);
		db.raiseAckFlag(txn, contactId, messageId);

		// The message ID should only be returned once
		Collection<MessageId> ids = db.getMessagesToAck(txn, contactId, 1234);
		assertEquals(Collections.singletonList(messageId), ids);

		// Remove the message ID
		db.lowerAckFlag(txn, contactId, Collections.singletonList(messageId));

		// The message ID should have been removed
		assertEquals(Collections.emptyList(), db.getMessagesToAck(txn,
				contactId, 1234));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testOutstandingMessageAcked() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// Retrieve the message from the database and mark it as sent
		Iterator<MessageId> it =
				db.getMessagesToSend(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		db.updateExpiryTime(txn, contactId, messageId, Integer.MAX_VALUE);

		// The message should no longer be sendable
		it = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Pretend that the message was acked
		db.raiseSeenFlag(txn, contactId, messageId);

		// The message still should not be sendable
		it = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetFreeSpace() throws Exception {
		byte[] largeBody = new byte[MAX_MESSAGE_LENGTH];
		for (int i = 0; i < largeBody.length; i++) largeBody[i] = (byte) i;
		Message message = new Message(messageId, groupId, timestamp, largeBody);
		Database<Connection> db = open(false);

		// Sanity check: there should be enough space on disk for this test
		assertTrue(testDir.getFreeSpace() > MAX_SIZE);

		// The free space should not be more than the allowed maximum size
		long free = db.getFreeSpace();
		assertTrue(free <= MAX_SIZE);
		assertTrue(free > 0);

		// Storing a message should reduce the free space
		Connection txn = db.startTransaction();
		db.addGroup(txn, group);
		db.addMessage(txn, message, true);
		db.commitTransaction(txn);
		assertTrue(db.getFreeSpace() < free);

		db.close();
	}

	@Test
	public void testCloseWaitsForCommit() throws Exception {
		final CountDownLatch closing = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final AtomicBoolean transactionFinished = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread close = new Thread() {
			@Override
			public void run() {
				try {
					closing.countDown();
					db.close();
					if (!transactionFinished.get()) error.set(true);
					closed.countDown();
				} catch (Exception e) {
					error.set(true);
				}
			}
		};
		close.start();
		closing.await();
		// Do whatever the transaction needs to do
		Thread.sleep(10);
		transactionFinished.set(true);
		// Commit the transaction
		db.commitTransaction(txn);
		// The other thread should now terminate
		assertTrue(closed.await(5, SECONDS));
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testCloseWaitsForAbort() throws Exception {
		final CountDownLatch closing = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final AtomicBoolean transactionFinished = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread close = new Thread() {
			@Override
			public void run() {
				try {
					closing.countDown();
					db.close();
					if (!transactionFinished.get()) error.set(true);
					closed.countDown();
				} catch (Exception e) {
					error.set(true);
				}
			}
		};
		close.start();
		closing.await();
		// Do whatever the transaction needs to do
		Thread.sleep(10);
		transactionFinished.set(true);
		// Abort the transaction
		db.abortTransaction(txn);
		// The other thread should now terminate
		assertTrue(closed.await(5, SECONDS));
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testUpdateSettings() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a transport to the database
		db.addTransport(txn, transportId, 123);

		// Set the transport config
		Settings s = new Settings();
		s.put("foo", "foo");
		s.put("bar", "bar");
		db.mergeSettings(txn, s, "test");
		assertEquals(s, db.getSettings(txn, "test"));

		// Update one of the properties and add another
		Settings s1 = new Settings();
		s1.put("bar", "baz");
		s1.put("bam", "bam");
		db.mergeSettings(txn, s1, "test");
		Settings merged = new Settings();
		merged.put("foo", "foo");
		merged.put("bar", "baz");
		merged.put("bam", "bam");
		assertEquals(merged, db.getSettings(txn, "test"));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContainsVisibleMessageRequiresMessageInDatabase()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);

		// The message is not in the database
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContainsVisibleMessageRequiresLocalSubscription()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with a subscription
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);

		// There's no local subscription for the group
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContainsVisibleMessageRequiresVisibileSubscription()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The subscription is not visible
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testVisibility() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);

		// The group should not be visible to the contact
		assertEquals(Collections.emptyList(), db.getVisibility(txn, groupId));

		// Make the group visible to the contact
		db.addVisibility(txn, contactId, groupId);
		assertEquals(Collections.singletonList(contactId),
				db.getVisibility(txn, groupId));

		// Make the group invisible again
		db.removeVisibility(txn, contactId, groupId);
		assertEquals(Collections.emptyList(), db.getVisibility(txn, groupId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMultipleSubscriptionsAndUnsubscriptions() throws Exception {
		// Create some groups
		List<Group> groups = new ArrayList<Group>();
		for (int i = 0; i < 100; i++) {
			GroupId id = new GroupId(TestUtils.getRandomId());
			ClientId clientId = new ClientId(TestUtils.getRandomId());
			byte[] descriptor = new byte[MAX_GROUP_DESCRIPTOR_LENGTH];
			groups.add(new Group(id, clientId, descriptor));
		}

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to the groups
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		for (Group g : groups) db.addGroup(txn, g);

		// Make the groups visible to the contact
		Collections.shuffle(groups);
		for (Group g : groups) db.addVisibility(txn, contactId, g.getId());

		// Make some of the groups invisible to the contact and remove them all
		Collections.shuffle(groups);
		for (Group g : groups) {
			if (Math.random() < 0.5)
				db.removeVisibility(txn, contactId, g.getId());
			db.removeGroup(txn, g.getId());
		}

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTransportKeys() throws Exception {
		TransportKeys keys = createTransportKeys();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Initially there should be no transport keys in the database
		assertEquals(Collections.emptyMap(),
				db.getTransportKeys(txn, transportId));

		// Add the contact, the transport and the transport keys
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addTransport(txn, transportId, 123);
		db.addTransportKeys(txn, contactId, keys);

		// Retrieve the transport keys
		Map<ContactId, TransportKeys> newKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(1, newKeys.size());
		Entry<ContactId, TransportKeys> e =
				newKeys.entrySet().iterator().next();
		assertEquals(contactId, e.getKey());
		TransportKeys k = e.getValue();
		assertEquals(transportId, k.getTransportId());
		assertKeysEquals(keys.getPreviousIncomingKeys(),
				k.getPreviousIncomingKeys());
		assertKeysEquals(keys.getCurrentIncomingKeys(),
				k.getCurrentIncomingKeys());
		assertKeysEquals(keys.getNextIncomingKeys(),
				k.getNextIncomingKeys());
		assertKeysEquals(keys.getCurrentOutgoingKeys(),
				k.getCurrentOutgoingKeys());

		// Removing the contact should remove the transport keys
		db.removeContact(txn, contactId);
		assertEquals(Collections.emptyMap(),
				db.getTransportKeys(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	private void assertKeysEquals(IncomingKeys expected, IncomingKeys actual) {
		assertArrayEquals(expected.getTagKey().getBytes(),
				actual.getTagKey().getBytes());
		assertArrayEquals(expected.getHeaderKey().getBytes(),
				actual.getHeaderKey().getBytes());
		assertEquals(expected.getRotationPeriod(), actual.getRotationPeriod());
		assertEquals(expected.getWindowBase(), actual.getWindowBase());
		assertArrayEquals(expected.getWindowBitmap(), actual.getWindowBitmap());
	}

	private void assertKeysEquals(OutgoingKeys expected, OutgoingKeys actual) {
		assertArrayEquals(expected.getTagKey().getBytes(),
				actual.getTagKey().getBytes());
		assertArrayEquals(expected.getHeaderKey().getBytes(),
				actual.getHeaderKey().getBytes());
		assertEquals(expected.getRotationPeriod(), actual.getRotationPeriod());
		assertEquals(expected.getStreamCounter(), actual.getStreamCounter());
	}

	@Test
	public void testIncrementStreamCounter() throws Exception {
		TransportKeys keys = createTransportKeys();
		long rotationPeriod = keys.getCurrentOutgoingKeys().getRotationPeriod();
		long streamCounter = keys.getCurrentOutgoingKeys().getStreamCounter();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact, transport and transport keys
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addTransport(txn, transportId, 123);
		db.updateTransportKeys(txn, Collections.singletonMap(contactId, keys));

		// Increment the stream counter twice and retrieve the transport keys
		db.incrementStreamCounter(txn, contactId, transportId, rotationPeriod);
		db.incrementStreamCounter(txn, contactId, transportId, rotationPeriod);
		Map<ContactId, TransportKeys> newKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(1, newKeys.size());
		Entry<ContactId, TransportKeys> e =
				newKeys.entrySet().iterator().next();
		assertEquals(contactId, e.getKey());
		TransportKeys k = e.getValue();
		assertEquals(transportId, k.getTransportId());
		OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
		assertEquals(rotationPeriod, outCurr.getRotationPeriod());
		assertEquals(streamCounter + 2, outCurr.getStreamCounter());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetReorderingWindow() throws Exception {
		TransportKeys keys = createTransportKeys();
		long rotationPeriod = keys.getCurrentIncomingKeys().getRotationPeriod();
		long base = keys.getCurrentIncomingKeys().getWindowBase();
		byte[] bitmap = keys.getCurrentIncomingKeys().getWindowBitmap();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact, transport and transport keys
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addTransport(txn, transportId, 123);
		db.updateTransportKeys(txn, Collections.singletonMap(contactId, keys));

		// Update the reordering window and retrieve the transport keys
		random.nextBytes(bitmap);
		db.setReorderingWindow(txn, contactId, transportId, rotationPeriod,
				base + 1, bitmap);
		Map<ContactId, TransportKeys> newKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(1, newKeys.size());
		Entry<ContactId, TransportKeys> e =
				newKeys.entrySet().iterator().next();
		assertEquals(contactId, e.getKey());
		TransportKeys k = e.getValue();
		assertEquals(transportId, k.getTransportId());
		IncomingKeys inCurr = k.getCurrentIncomingKeys();
		assertEquals(rotationPeriod, inCurr.getRotationPeriod());
		assertEquals(base + 1, inCurr.getWindowBase());
		assertArrayEquals(bitmap, inCurr.getWindowBitmap());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetAvailableGroups() throws Exception {
		ContactId contactId1 = new ContactId(2);
		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		Author author1 = new Author(authorId1, "Carol",
				new byte[MAX_PUBLIC_KEY_LENGTH]);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add two contacts who subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		assertEquals(contactId1, db.addContact(txn, author1, localAuthorId));
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);
		db.setGroups(txn, contactId1, Collections.singletonList(group), 1);

		// The group should be available
		assertEquals(Collections.emptyList(), db.getGroups(txn, clientId));
		assertEquals(Collections.singletonList(group),
				db.getAvailableGroups(txn, clientId));

		// Subscribe to the group - it should no longer be available
		db.addGroup(txn, group);
		assertEquals(Collections.singletonList(group),
				db.getGroups(txn, clientId));
		assertEquals(Collections.emptyList(),
				db.getAvailableGroups(txn, clientId));

		// Unsubscribe from the group - it should be available again
		db.removeGroup(txn, groupId);
		assertEquals(Collections.emptyList(), db.getGroups(txn, clientId));
		assertEquals(Collections.singletonList(group),
				db.getAvailableGroups(txn, clientId));

		// The first contact unsubscribes - it should still be available
		db.setGroups(txn, contactId, Collections.<Group>emptyList(), 2);
		assertEquals(Collections.emptyList(), db.getGroups(txn, clientId));
		assertEquals(Collections.singletonList(group),
				db.getAvailableGroups(txn, clientId));

		// The second contact unsubscribes - it should no longer be available
		db.setGroups(txn, contactId1, Collections.<Group>emptyList(), 2);
		assertEquals(Collections.emptyList(), db.getGroups(txn, clientId));
		assertEquals(Collections.emptyList(),
				db.getAvailableGroups(txn, clientId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetContactsByLocalAuthorId() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a local author - no contacts should be associated
		db.addLocalAuthor(txn, localAuthor);
		Collection<ContactId> contacts = db.getContacts(txn, localAuthorId);
		assertEquals(Collections.emptyList(), contacts);

		// Add a contact associated with the local author
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		contacts = db.getContacts(txn, localAuthorId);
		assertEquals(Collections.singletonList(contactId), contacts);

		// Remove the local author - the contact should be removed
		db.removeLocalAuthor(txn, localAuthorId);
		contacts = db.getContacts(txn, localAuthorId);
		assertEquals(Collections.emptyList(), contacts);
		assertFalse(db.containsContact(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testOfferedMessages() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact - initially there should be no offered messages
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		assertEquals(0, db.countOfferedMessages(txn, contactId));

		// Add some offered messages and count them
		List<MessageId> ids = new ArrayList<MessageId>();
		for (int i = 0; i < 10; i++) {
			MessageId m = new MessageId(TestUtils.getRandomId());
			db.addOfferedMessage(txn, contactId, m);
			ids.add(m);
		}
		assertEquals(10, db.countOfferedMessages(txn, contactId));

		// Remove some of the offered messages and count again
		List<MessageId> half = ids.subList(0, 5);
		db.removeOfferedMessages(txn, contactId, half);
		assertTrue(db.removeOfferedMessage(txn, contactId, ids.get(5)));
		assertEquals(4, db.countOfferedMessages(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContactUnsubscribingResetsMessageStatus() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact who subscribes to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);

		// Subscribe to the group and make it visible to the contact
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);

		// Add a message - it should be sendable to the contact
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, false);
		Collection<MessageId> sendable = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), sendable);

		// Mark the message as seen - it should no longer be sendable
		db.raiseSeenFlag(txn, contactId, messageId);
		sendable = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertEquals(Collections.emptyList(), sendable);

		// The contact unsubscribes - the message should not be sendable
		db.setGroups(txn, contactId, Collections.<Group>emptyList(), 2);
		sendable = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertEquals(Collections.emptyList(), sendable);

		// The contact resubscribes - the message should be sendable again
		db.setGroups(txn, contactId, Collections.singletonList(group), 3);
		sendable = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), sendable);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGroupMetadata() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group
		db.addGroup(txn, group);

		// Attach some metadata to the group
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[]{'b', 'a', 'r'});
		metadata.put("baz", new byte[]{'b', 'a', 'm'});
		db.mergeGroupMetadata(txn, groupId, metadata);

		// Retrieve the metadata for the group
		Metadata retrieved = db.getGroupMetadata(txn, groupId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Update the metadata
		metadata.put("foo", REMOVE);
		metadata.put("baz", new byte[] {'q', 'u', 'x'});
		db.mergeGroupMetadata(txn, groupId, metadata);

		// Retrieve the metadata again
		retrieved = db.getGroupMetadata(txn, groupId);
		assertEquals(1, retrieved.size());
		assertFalse(retrieved.containsKey("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessageMetadata() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, true);

		// Attach some metadata to the message
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[]{'b', 'a', 'r'});
		metadata.put("baz", new byte[]{'b', 'a', 'm'});
		db.mergeMessageMetadata(txn, messageId, metadata);

		// Retrieve the metadata for the message
		Metadata retrieved = db.getMessageMetadata(txn, messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Retrieve the metadata for the group
		Map<MessageId, Metadata> all = db.getMessageMetadata(txn, groupId);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId));
		retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Update the metadata
		metadata.put("foo", REMOVE);
		metadata.put("baz", new byte[] {'q', 'u', 'x'});
		db.mergeMessageMetadata(txn, messageId, metadata);

		// Retrieve the metadata again
		retrieved = db.getMessageMetadata(txn, messageId);
		assertEquals(1, retrieved.size());
		assertFalse(retrieved.containsKey("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Retrieve the metadata for the group again
		all = db.getMessageMetadata(txn, groupId);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId));
		retrieved = all.get(messageId);
		assertEquals(1, retrieved.size());
		assertFalse(retrieved.containsKey("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageStatus() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact who subscribes to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.setGroups(txn, contactId, Collections.singletonList(group), 1);

		// Subscribe to the group and make it visible to the contact
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);

		// Add a message to the group
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The message should not be sent or seen
		MessageStatus status = db.getMessageStatus(txn, contactId, messageId);
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertFalse(status.isSent());
		assertFalse(status.isSeen());

		// The same status should be returned when querying by group
		Collection<MessageStatus> statuses = db.getMessageStatus(txn,
				contactId, groupId);
		assertEquals(1, statuses.size());
		status = statuses.iterator().next();
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertFalse(status.isSent());
		assertFalse(status.isSeen());

		// Pretend the message was sent to the contact
		db.updateExpiryTime(txn, contactId, messageId, Integer.MAX_VALUE);

		// The message should be sent but not seen
		status = db.getMessageStatus(txn, contactId, messageId);
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertTrue(status.isSent());
		assertFalse(status.isSeen());

		// The same status should be returned when querying by group
		statuses = db.getMessageStatus(txn, contactId, groupId);
		assertEquals(1, statuses.size());
		status = statuses.iterator().next();
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertTrue(status.isSent());
		assertFalse(status.isSeen());

		// Pretend the message was acked by the contact
		db.raiseSeenFlag(txn, contactId, messageId);

		// The message should be sent and seen
		status = db.getMessageStatus(txn, contactId, messageId);
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertTrue(status.isSent());
		assertTrue(status.isSeen());

		// The same status should be returned when querying by group
		statuses = db.getMessageStatus(txn, contactId, groupId);
		assertEquals(1, statuses.size());
		status = statuses.iterator().next();
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertTrue(status.isSent());
		assertTrue(status.isSeen());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testExceptionHandling() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();
		try {
			// Ask for a nonexistent message - an exception should be thrown
			db.getRawMessage(txn, messageId);
			fail();
		} catch (DbException expected) {
			// It should be possible to abort the transaction without error
			db.abortTransaction(txn);
		}
		// It should be possible to close the database cleanly
		db.close();
	}

	private Database<Connection> open(boolean resume) throws Exception {
		Database<Connection> db = new H2Database(new TestDatabaseConfig(testDir,
				MAX_SIZE), new SystemClock());
		if (!resume) TestUtils.deleteTestDirectory(testDir);
		db.open();
		return db;
	}

	private TransportKeys createTransportKeys() {
		SecretKey inPrevTagKey = TestUtils.createSecretKey();
		SecretKey inPrevHeaderKey = TestUtils.createSecretKey();
		IncomingKeys inPrev = new IncomingKeys(inPrevTagKey, inPrevHeaderKey,
				1, 123, new byte[4]);
		SecretKey inCurrTagKey = TestUtils.createSecretKey();
		SecretKey inCurrHeaderKey = TestUtils.createSecretKey();
		IncomingKeys inCurr = new IncomingKeys(inCurrTagKey, inCurrHeaderKey,
				2, 234, new byte[4]);
		SecretKey inNextTagKey = TestUtils.createSecretKey();
		SecretKey inNextHeaderKey = TestUtils.createSecretKey();
		IncomingKeys inNext = new IncomingKeys(inNextTagKey, inNextHeaderKey,
				3, 345, new byte[4]);
		SecretKey outCurrTagKey = TestUtils.createSecretKey();
		SecretKey outCurrHeaderKey = TestUtils.createSecretKey();
		OutgoingKeys outCurr = new OutgoingKeys(outCurrTagKey, outCurrHeaderKey,
				2, 456);
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr);
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
