package org.briarproject.db;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseConfig;
import org.briarproject.TestUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.StorageStatus;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.settings.Settings;
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
import java.security.SecureRandom;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import static org.briarproject.api.sync.ValidationManager.Validity.UNKNOWN;
import static org.briarproject.api.sync.ValidationManager.Validity.VALID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class H2DatabaseTest extends BriarTestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	private static final int MAX_SIZE = 5 * ONE_MEGABYTE;

	private final File testDir = TestUtils.getTestDirectory();
	private final Random random = new Random();
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
		groupId = new GroupId(TestUtils.getRandomId());
		ClientId clientId = new ClientId(TestUtils.getRandomId());
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
		db.addMessage(txn, message, VALID, true);
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
	public void testRemovingGroupRemovesMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, VALID, true);

		// Removing the group should remove the message
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

		// Add a contact, a group and a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, VALID, true);

		// The message has no status yet, so it should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		// Adding a status with seen = false should make the message sendable
		db.addStatus(txn, contactId, messageId, false, false);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertEquals(Collections.singletonList(messageId), ids);

		// Changing the status to seen = true should make the message unsendable
		db.raiseSeenFlag(txn, contactId, messageId);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustBeValid() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a group and an unvalidated message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, UNKNOWN, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The message has not been validated, so it should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		// Marking the message valid should make it sendable
		db.setMessageValid(txn, messageId, true);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertEquals(Collections.singletonList(messageId), ids);

		// Marking the message invalid should make it unsendable
		db.setMessageValid(txn, messageId, false);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustBeShared() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a group and an unshared message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, VALID, false);
		db.addStatus(txn, contactId, messageId, false, false);

		// The message is not shared, so it should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		// Sharing the message should make it sendable
		db.setMessageShared(txn, messageId, true);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertEquals(Collections.singletonList(messageId), ids);

		// Unsharing the message should make it unsendable
		db.setMessageShared(txn, messageId, false);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustFitCapacity() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a group and a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, VALID, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The message is sendable, but too large to send
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				size - 1);
		assertTrue(ids.isEmpty());

		// The message is just the right size to send
		ids = db.getMessagesToSend(txn, contactId, size);
		assertEquals(Collections.singletonList(messageId), ids);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustBeVisible() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a group and a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addMessage(txn, message, VALID, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The group is not visible to the contact, so the message
		// should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		// Making the group visible should make the message sendable
		db.addVisibility(txn, contactId, groupId);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertEquals(Collections.singletonList(messageId), ids);

		// Making the group invisible should make the message unsendable
		db.removeVisibility(txn, contactId, groupId);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessagesToAck() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);

		// Add some messages to ack
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new Message(messageId1, groupId, timestamp, raw);
		db.addMessage(txn, message, VALID, true);
		db.addStatus(txn, contactId, messageId, false, true);
		db.raiseAckFlag(txn, contactId, messageId);
		db.addMessage(txn, message1, VALID, true);
		db.addStatus(txn, contactId, messageId1, false, true);
		db.raiseAckFlag(txn, contactId, messageId1);

		// Both message IDs should be returned
		Collection<MessageId> ids = db.getMessagesToAck(txn, contactId, 1234);
		assertEquals(Arrays.asList(messageId, messageId1), ids);

		// Remove both message IDs
		db.lowerAckFlag(txn, contactId, Arrays.asList(messageId, messageId1));

		// Both message IDs should have been removed
		assertEquals(Collections.emptyList(), db.getMessagesToAck(txn,
				contactId, 1234));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testOutstandingMessageAcked() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a group and a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, VALID, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// Retrieve the message from the database and mark it as sent
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), ids);
		db.updateExpiryTime(txn, contactId, messageId, Integer.MAX_VALUE);

		// The message should no longer be sendable
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());

		// Pretend that the message was acked
		db.raiseSeenFlag(txn, contactId, messageId);

		// The message still should not be sendable
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());

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
		db.addMessage(txn, message, VALID, true);
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

		// Store some settings
		Settings s = new Settings();
		s.put("foo", "foo");
		s.put("bar", "bar");
		db.mergeSettings(txn, s, "test");
		assertEquals(s, db.getSettings(txn, "test"));

		// Update one of the settings and add another
		Settings s1 = new Settings();
		s1.put("bar", "baz");
		s1.put("bam", "bam");
		db.mergeSettings(txn, s1, "test");

		// Check that the settings were merged
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

		// Add a contact and a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);

		// The message is not in the database
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContainsVisibleMessageRequiresGroupInDatabase()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));

		// The group is not in the database
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContainsVisibleMessageRequiresVisibileGroup()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a group and a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addMessage(txn, message, VALID, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The group is not visible
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testVisibility() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and a group
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
	public void testMultipleGroupChanges() throws Exception {
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

		// Add a contact and the groups
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
		db.addMessage(txn, message, VALID, true);

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

		// Delete the metadata
		db.deleteMessageMetadata(txn, messageId);

		// Retrieve the metadata again
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());

		// Retrieve the metadata for the group again
		all = db.getMessageMetadata(txn, groupId);
		assertTrue(all.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageStatus() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));

		// Add a group and make it visible to the contact
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);

		// Add a message to the group
		db.addMessage(txn, message, VALID, true);
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
	public void testGroupsVisibleToContacts() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);

		// The group should not be visible to the contact
		assertFalse(db.containsVisibleGroup(txn, contactId, groupId));

		// Make the group visible to the contact
		db.addVisibility(txn, contactId, groupId);
		assertTrue(db.containsVisibleGroup(txn, contactId, groupId));

		// Make the group invisible to the contact
		db.removeVisibility(txn, contactId, groupId);
		assertFalse(db.containsVisibleGroup(txn, contactId, groupId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testDifferentLocalPseudonymsCanHaveTheSameContact()
			throws Exception {
		AuthorId localAuthorId1 = new AuthorId(TestUtils.getRandomId());
		LocalAuthor localAuthor1 = new LocalAuthor(localAuthorId1, "Carol",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[123], timestamp,
				StorageStatus.ACTIVE);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add two local pseudonyms
		db.addLocalAuthor(txn, localAuthor);
		db.addLocalAuthor(txn, localAuthor1);

		// Add the same contact for each local pseudonym
		ContactId contactId = db.addContact(txn, author, localAuthorId);
		ContactId contactId1 = db.addContact(txn, author, localAuthorId1);

		// The contacts should be distinct
		assertNotEquals(contactId, contactId1);
		assertEquals(2, db.getContacts(txn).size());
		assertEquals(1, db.getContacts(txn, localAuthorId).size());
		assertEquals(1, db.getContacts(txn, localAuthorId1).size());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testDeleteMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a group and a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, VALID, true);
		db.addStatus(txn, contactId, messageId, false, false);

		// The message should be visible to the contact
		assertTrue(db.containsVisibleMessage(txn, contactId, messageId));

		// The message should be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertEquals(Collections.singletonList(messageId), ids);

		// The raw message should not be null
		assertNotNull(db.getRawMessage(txn, messageId));

		// Delete the message
		db.deleteMessage(txn, messageId);

		// The message should be visible to the contact
		assertTrue(db.containsVisibleMessage(txn, contactId, messageId));

		// The message should not be sendable
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		// The raw message should be null
		assertNull(db.getRawMessage(txn, messageId));

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
				MAX_SIZE), new SecureRandom(), new SystemClock());
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
