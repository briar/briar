package org.briarproject.db;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseConfig;
import org.briarproject.TestUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
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
import org.briarproject.api.sync.ValidationManager.State;
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
import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_LENGTH;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.api.sync.ValidationManager.State.PENDING;
import static org.briarproject.api.sync.ValidationManager.State.UNKNOWN;
import static org.briarproject.api.sync.ValidationManager.State.VALID;
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
		byte[] descriptor = new byte[0];
		group = new Group(groupId, clientId, descriptor);
		AuthorId authorId = new AuthorId(TestUtils.getRandomId());
		author = new Author(authorId, "Alice", new byte[MAX_PUBLIC_KEY_LENGTH]);
		localAuthorId = new AuthorId(TestUtils.getRandomId());
		timestamp = System.currentTimeMillis();
		localAuthor = new LocalAuthor(localAuthorId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[123], timestamp);
		messageId = new MessageId(TestUtils.getRandomId());
		size = 1234;
		raw = TestUtils.getRandomBytes(size);
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsGroup(txn, groupId));
		db.addGroup(txn, group);
		assertTrue(db.containsGroup(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.addMessage(txn, message, DELIVERED, true);
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
		db.addMessage(txn, message, DELIVERED, true);

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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, DELIVERED, true);

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
	public void testSendableMessagesMustBeDelivered() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a group and an unvalidated message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
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

		// Marking the message delivered should make it sendable
		db.setMessageState(txn, messageId, DELIVERED);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertEquals(Collections.singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertEquals(Collections.singletonList(messageId), ids);

		// Marking the message invalid should make it unsendable
		db.setMessageState(txn, messageId, INVALID);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		// Marking the message valid should make it unsendable
		// TODO do we maybe want to already send valid messages? If we do, we need also to call db.setMessageShared() earlier.
		db.setMessageState(txn, messageId, VALID);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100);
		assertTrue(ids.isEmpty());

		// Marking the message pending should make it unsendable
		// TODO do we maybe want to already send pending messages? If we do, we need also to call db.setMessageShared() earlier.
		db.setMessageState(txn, messageId, PENDING);
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, DELIVERED, false);
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, DELIVERED, true);
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
	public void testMessagesToAck() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);

		// Add some messages to ack
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new Message(messageId1, groupId, timestamp, raw);
		db.addMessage(txn, message, DELIVERED, true);
		db.addStatus(txn, contactId, messageId, false, true);
		db.raiseAckFlag(txn, contactId, messageId);
		db.addMessage(txn, message1, DELIVERED, true);
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, DELIVERED, true);
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
		db.addMessage(txn, message, DELIVERED, true);
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));

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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true);
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
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
		List<Group> groups = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			GroupId id = new GroupId(TestUtils.getRandomId());
			ClientId clientId = new ClientId(TestUtils.getRandomId());
			byte[] descriptor = new byte[0];
			groups.add(new Group(id, clientId, descriptor));
		}

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and the groups
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		db.addTransport(txn, transportId, 123);
		db.updateTransportKeys(txn, Collections.singletonMap(contactId, keys));

		// Update the reordering window and retrieve the transport keys
		new Random().nextBytes(bitmap);
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		assertEquals(0, db.countOfferedMessages(txn, contactId));

		// Add some offered messages and count them
		List<MessageId> ids = new ArrayList<>();
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
		db.addMessage(txn, message, DELIVERED, true);

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
	public void testMessageMetadataOnlyForDeliveredMessages() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true);

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
		Map<MessageId, Metadata> map = db.getMessageMetadata(txn, groupId);
		assertEquals(1, map.size());
		assertTrue(map.get(messageId).containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), map.get(messageId).get("foo"));
		assertTrue(map.get(messageId).containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), map.get(messageId).get("baz"));

		// No metadata for unknown messages
		db.setMessageState(txn, messageId, UNKNOWN);
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());
		map = db.getMessageMetadata(txn, groupId);
		assertTrue(map.isEmpty());

		// No metadata for invalid messages
		db.setMessageState(txn, messageId, INVALID);
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());
		map = db.getMessageMetadata(txn, groupId);
		assertTrue(map.isEmpty());

		// No metadata for valid messages
		db.setMessageState(txn, messageId, VALID);
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());
		map = db.getMessageMetadata(txn, groupId);
		assertTrue(map.isEmpty());

		// No metadata for pending messages
		db.setMessageState(txn, messageId, PENDING);
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());
		map = db.getMessageMetadata(txn, groupId);
		assertTrue(map.isEmpty());

		// validator gets also metadata for pending messages
		retrieved = db.getMessageMetadataForValidator(txn, messageId);
		assertFalse(retrieved.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMetadataQueries() throws Exception {
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new Message(messageId1, groupId, timestamp, raw);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and two messages
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true);
		db.addMessage(txn, message1, DELIVERED, true);

		// Attach some metadata to the messages
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[]{'b', 'a', 'r'});
		metadata.put("baz", new byte[]{'b', 'a', 'm'});
		db.mergeMessageMetadata(txn, messageId, metadata);
		Metadata metadata1 = new Metadata();
		metadata1.put("foo", new byte[]{'q', 'u', 'x'});
		db.mergeMessageMetadata(txn, messageId1, metadata1);

		// Retrieve all the metadata for the group
		Map<MessageId, Metadata> all = db.getMessageMetadata(txn, groupId);
		assertEquals(2, all.size());
		assertTrue(all.containsKey(messageId));
		assertTrue(all.containsKey(messageId1));
		Metadata retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));
		retrieved = all.get(messageId1);
		assertEquals(1, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata1.get("foo"), retrieved.get("foo"));

		// Query the metadata with an empty query
		Metadata query = new Metadata();
		all = db.getMessageMetadata(txn, groupId, query);
		assertEquals(2, all.size());
		assertTrue(all.containsKey(messageId));
		assertTrue(all.containsKey(messageId1));
		retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));
		retrieved = all.get(messageId1);
		assertEquals(1, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata1.get("foo"), retrieved.get("foo"));

		// Use a single-term query that matches the first message
		query = new Metadata();
		query.put("foo", metadata.get("foo"));
		all = db.getMessageMetadata(txn, groupId, query);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId));
		retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Use a single-term query that matches the second message
		query = new Metadata();
		query.put("foo", metadata1.get("foo"));
		all = db.getMessageMetadata(txn, groupId, query);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId1));
		retrieved = all.get(messageId1);
		assertEquals(1, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata1.get("foo"), retrieved.get("foo"));

		// Use a multi-term query that matches the first message
		query = new Metadata();
		query.put("foo", metadata.get("foo"));
		query.put("baz", metadata.get("baz"));
		all = db.getMessageMetadata(txn, groupId, query);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId));
		retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Use a multi-term query that doesn't match any messages
		query = new Metadata();
		query.put("foo", metadata1.get("foo"));
		query.put("baz", metadata.get("baz"));
		all = db.getMessageMetadata(txn, groupId, query);
		assertTrue(all.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMetadataQueriesOnlyForDeliveredMessages() throws Exception {
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new Message(messageId1, groupId, timestamp, raw);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and two messages
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true);
		db.addMessage(txn, message1, DELIVERED, true);

		// Attach some metadata to the messages
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[]{'b', 'a', 'r'});
		metadata.put("baz", new byte[]{'b', 'a', 'm'});
		db.mergeMessageMetadata(txn, messageId, metadata);
		Metadata metadata1 = new Metadata();
		metadata1.put("foo", new byte[]{'b', 'a', 'r'});
		db.mergeMessageMetadata(txn, messageId1, metadata1);

		for (int i = 1; i <= 2; i++) {
			Metadata query;
			if (i == 1) {
				// Query the metadata with an empty query
				query = new Metadata();
			} else {
				// Query for foo
				query = new Metadata();
				query.put("foo", metadata.get("foo"));
			}

			db.setMessageState(txn, messageId, DELIVERED);
			db.setMessageState(txn, messageId1, DELIVERED);
			Map<MessageId, Metadata> all =
					db.getMessageMetadata(txn, groupId, query);
			assertEquals(2, all.size());
			assertEquals(2, all.get(messageId).size());
			assertEquals(1, all.get(messageId1).size());

			// No metadata for unknown messages
			db.setMessageState(txn, messageId, UNKNOWN);
			db.setMessageState(txn, messageId1, UNKNOWN);
			all = db.getMessageMetadata(txn, groupId, query);
			assertTrue(all.isEmpty());

			// No metadata for invalid messages
			db.setMessageState(txn, messageId, INVALID);
			db.setMessageState(txn, messageId1, INVALID);
			all = db.getMessageMetadata(txn, groupId, query);
			assertTrue(all.isEmpty());

			// No metadata for valid messages
			db.setMessageState(txn, messageId, VALID);
			db.setMessageState(txn, messageId1, VALID);
			all = db.getMessageMetadata(txn, groupId, query);
			assertTrue(all.isEmpty());

			// No metadata for pending messages
			db.setMessageState(txn, messageId, PENDING);
			db.setMessageState(txn, messageId1, PENDING);
			all = db.getMessageMetadata(txn, groupId, query);
			assertTrue(all.isEmpty());
		}

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessageDependencies() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, VALID, true);

		// Create more messages
		MessageId mId1 = new MessageId(TestUtils.getRandomId());
		MessageId mId2 = new MessageId(TestUtils.getRandomId());
		MessageId dId1 = new MessageId(TestUtils.getRandomId());
		MessageId dId2 = new MessageId(TestUtils.getRandomId());
		Message m1 = new Message(mId1, groupId, timestamp, raw);
		Message m2 = new Message(mId2, groupId, timestamp, raw);

		// Add new messages
		db.addMessage(txn, m1, VALID, true);
		db.addMessage(txn, m2, INVALID, true);

		// Add dependencies
		db.addMessageDependency(txn, messageId, mId1);
		db.addMessageDependency(txn, messageId, mId2);
		db.addMessageDependency(txn, mId1, dId1);
		db.addMessageDependency(txn, mId2, dId2);

		Map<MessageId, State> dependencies;

		// Retrieve dependencies for root
		dependencies = db.getMessageDependencies(txn, messageId);
		assertEquals(2, dependencies.size());
		assertEquals(VALID, dependencies.get(mId1));
		assertEquals(INVALID, dependencies.get(mId2));

		// Retrieve dependencies for m1
		dependencies = db.getMessageDependencies(txn, mId1);
		assertEquals(1, dependencies.size());
		assertEquals(UNKNOWN, dependencies.get(dId1));

		// Retrieve dependencies for m2
		dependencies = db.getMessageDependencies(txn, mId2);
		assertEquals(1, dependencies.size());
		assertEquals(UNKNOWN, dependencies.get(dId2));

		// Make sure d's have no dependencies
		dependencies = db.getMessageDependencies(txn, dId1);
		assertTrue(dependencies.isEmpty());
		dependencies = db.getMessageDependencies(txn, dId2);
		assertTrue(dependencies.isEmpty());

		Map<MessageId, State> dependents;

		// Root message does not have dependents
		dependents = db.getMessageDependents(txn, messageId);
		assertTrue(dependents.isEmpty());

		// The root message depends on both m's
		dependents = db.getMessageDependents(txn, mId1);
		assertEquals(1, dependents.size());
		assertEquals(VALID, dependents.get(messageId));
		dependents = db.getMessageDependents(txn, mId2);
		assertEquals(1, dependents.size());
		assertEquals(VALID, dependents.get(messageId));

		// Both m's depend on the d's
		dependents = db.getMessageDependents(txn, dId1);
		assertEquals(1, dependents.size());
		assertEquals(VALID, dependents.get(mId1));
		dependents = db.getMessageDependents(txn, dId2);
		assertEquals(1, dependents.size());
		assertEquals(INVALID, dependents.get(mId2));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessageDependenciesInSameGroup() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true);

		// Add a second group
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, group.getClientId(),
				TestUtils.getRandomBytes(42));
		db.addGroup(txn, group1);

		// Add a message to the second group
		MessageId mId1 = new MessageId(TestUtils.getRandomId());
		Message m1 = new Message(mId1, groupId1, timestamp, raw);
		db.addMessage(txn, m1, DELIVERED, true);

		// Create a fake dependency as well
		MessageId mId2 = new MessageId(TestUtils.getRandomId());

		// Create and add a real and proper dependency
		MessageId mId3 = new MessageId(TestUtils.getRandomId());
		Message m3 = new Message(mId3, groupId, timestamp, raw);
		db.addMessage(txn, m3, PENDING, true);

		// Add dependencies
		db.addMessageDependency(txn, messageId, mId1);
		db.addMessageDependency(txn, messageId, mId2);
		db.addMessageDependency(txn, messageId, mId3);

		// Return invalid dependencies for delivered message m1
		Map<MessageId, State> dependencies;
		dependencies = db.getMessageDependencies(txn, messageId);
		assertEquals(INVALID, dependencies.get(mId1));
		assertEquals(UNKNOWN, dependencies.get(mId2));
		assertEquals(PENDING, dependencies.get(mId3));

		// Return invalid dependencies for valid message m1
		db.setMessageState(txn, mId1, VALID);
		dependencies = db.getMessageDependencies(txn, messageId);
		assertEquals(INVALID, dependencies.get(mId1));
		assertEquals(UNKNOWN, dependencies.get(mId2));
		assertEquals(PENDING, dependencies.get(mId3));

		// Return invalid dependencies for pending message m1
		db.setMessageState(txn, mId1, PENDING);
		dependencies = db.getMessageDependencies(txn, messageId);
		assertEquals(INVALID, dependencies.get(mId1));
		assertEquals(UNKNOWN, dependencies.get(mId2));
		assertEquals(PENDING, dependencies.get(mId3));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessagesForValidationAndDelivery() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, VALID, true);

		// Create more messages
		MessageId mId1 = new MessageId(TestUtils.getRandomId());
		MessageId mId2 = new MessageId(TestUtils.getRandomId());
		MessageId mId3 = new MessageId(TestUtils.getRandomId());
		MessageId mId4 = new MessageId(TestUtils.getRandomId());
		Message m1 = new Message(mId1, groupId, timestamp, raw);
		Message m2 = new Message(mId2, groupId, timestamp, raw);
		Message m3 = new Message(mId3, groupId, timestamp, raw);
		Message m4 = new Message(mId4, groupId, timestamp, raw);

		// Add new messages with different states
		db.addMessage(txn, m1, UNKNOWN, true);
		db.addMessage(txn, m2, INVALID, true);
		db.addMessage(txn, m3, PENDING, true);
		db.addMessage(txn, m4, DELIVERED, true);

		Collection<MessageId> result;

		// Retrieve messages to be validated
		result = db.getMessagesToValidate(txn, group.getClientId());
		assertEquals(1, result.size());
		assertTrue(result.contains(mId1));

		// Retrieve messages to be delivered
		result = db.getMessagesToDeliver(txn, group.getClientId());
		assertEquals(1, result.size());
		assertTrue(result.contains(messageId));

		// Retrieve pending messages
		result = db.getPendingMessages(txn, group.getClientId());
		assertEquals(1, result.size());
		assertTrue(result.contains(mId3));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageStatus() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));

		// Add a group and make it visible to the contact
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);

		// Add a message to the group
		db.addMessage(txn, message, DELIVERED, true);
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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
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
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[123], timestamp);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add two local pseudonyms
		db.addLocalAuthor(txn, localAuthor);
		db.addLocalAuthor(txn, localAuthor1);

		// Add the same contact for each local pseudonym
		ContactId contactId =
				db.addContact(txn, author, localAuthorId, true, true);
		ContactId contactId1 =
				db.addContact(txn, author, localAuthorId1, true, true);

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
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));
		db.addGroup(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addMessage(txn, message, DELIVERED, true);
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
	public void testSetContactActive() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId,
				true, true));

		// The contact should be active
		Contact contact = db.getContact(txn, contactId);
		assertTrue(contact.isActive());

		// Set the contact inactive
		db.setContactActive(txn, contactId, false);

		// The contact should be inactive
		contact = db.getContact(txn, contactId);
		assertFalse(contact.isActive());

		// Set the contact active
		db.setContactActive(txn, contactId, true);

		// The contact should be active
		contact = db.getContact(txn, contactId);
		assertTrue(contact.isActive());

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
		SecretKey inPrevTagKey = TestUtils.getSecretKey();
		SecretKey inPrevHeaderKey = TestUtils.getSecretKey();
		IncomingKeys inPrev = new IncomingKeys(inPrevTagKey, inPrevHeaderKey,
				1, 123, new byte[4]);
		SecretKey inCurrTagKey = TestUtils.getSecretKey();
		SecretKey inCurrHeaderKey = TestUtils.getSecretKey();
		IncomingKeys inCurr = new IncomingKeys(inCurrTagKey, inCurrHeaderKey,
				2, 234, new byte[4]);
		SecretKey inNextTagKey = TestUtils.getSecretKey();
		SecretKey inNextHeaderKey = TestUtils.getSecretKey();
		IncomingKeys inNext = new IncomingKeys(inNextTagKey, inNextHeaderKey,
				3, 345, new byte[4]);
		SecretKey outCurrTagKey = TestUtils.getSecretKey();
		SecretKey outCurrHeaderKey = TestUtils.getSecretKey();
		OutgoingKeys outCurr = new OutgoingKeys(outCurrTagKey, outCurrHeaderKey,
				2, 456);
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr);
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
