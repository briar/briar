package org.briarproject.db;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseConfig;
import org.briarproject.TestMessage;
import org.briarproject.TestUtils;
import org.briarproject.api.Author;
import org.briarproject.api.AuthorId;
import org.briarproject.api.ContactId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageHeader;
import org.briarproject.api.sync.MessageId;
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
import static org.briarproject.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.sync.MessageHeader.State.STORED;
import static org.briarproject.api.sync.MessagingConstants.GROUP_SALT_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
	private final String contentType, subject;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message;
	private final TransportId transportId;
	private final ContactId contactId;

	public H2DatabaseTest() throws Exception {
		groupId = new GroupId(TestUtils.getRandomId());
		group = new Group(groupId, "Group", new byte[GROUP_SALT_LENGTH]);
		AuthorId authorId = new AuthorId(TestUtils.getRandomId());
		author = new Author(authorId, "Alice", new byte[MAX_PUBLIC_KEY_LENGTH]);
		localAuthorId = new AuthorId(TestUtils.getRandomId());
		localAuthor = new LocalAuthor(localAuthorId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100], 1234);
		messageId = new MessageId(TestUtils.getRandomId());
		contentType = "text/plain";
		subject = "Foo";
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		random.nextBytes(raw);
		message = new TestMessage(messageId, null, group, author, contentType,
				subject, timestamp, raw);
		transportId = new TransportId("id");
		contactId = new ContactId(1);
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
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
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(txn, transportId));
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
		Message message1 = new TestMessage(messageId1, null, group, author,
				contentType, subject, timestamp, raw);
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
		byte[] largeBody = new byte[ONE_MEGABYTE];
		for (int i = 0; i < largeBody.length; i++) largeBody[i] = (byte) i;
		Message message = new TestMessage(messageId, null, group, author,
				contentType, subject, timestamp, largeBody);
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
	public void testUpdateRemoteTransportProperties() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with a transport
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		TransportProperties p = new TransportProperties(
				Collections.singletonMap("foo", "bar"));
		db.setRemoteProperties(txn, contactId, transportId, p, 1);
		assertEquals(Collections.singletonMap(contactId, p),
				db.getRemoteProperties(txn, transportId));

		// Replace the transport properties
		TransportProperties p1 = new TransportProperties(
				Collections.singletonMap("baz", "bam"));
		db.setRemoteProperties(txn, contactId, transportId, p1, 2);
		assertEquals(Collections.singletonMap(contactId, p1),
				db.getRemoteProperties(txn, transportId));

		// Remove the transport properties
		TransportProperties p2 = new TransportProperties();
		db.setRemoteProperties(txn, contactId, transportId, p2, 3);
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUpdateLocalTransportProperties() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a transport to the database
		db.addTransport(txn, transportId, 123);

		// Set the transport properties
		TransportProperties p = new TransportProperties();
		p.put("foo", "foo");
		p.put("bar", "bar");
		db.mergeLocalProperties(txn, transportId, p);
		assertEquals(p, db.getLocalProperties(txn, transportId));
		assertEquals(Collections.singletonMap(transportId, p),
				db.getLocalProperties(txn));

		// Update one of the properties and add another
		TransportProperties p1 = new TransportProperties();
		p1.put("bar", "baz");
		p1.put("bam", "bam");
		db.mergeLocalProperties(txn, transportId, p1);
		TransportProperties merged = new TransportProperties();
		merged.put("foo", "foo");
		merged.put("bar", "baz");
		merged.put("bam", "bam");
		assertEquals(merged, db.getLocalProperties(txn, transportId));
		assertEquals(Collections.singletonMap(transportId, merged),
				db.getLocalProperties(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUpdateTransportConfig() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a transport to the database
		db.addTransport(txn, transportId, 123);

		// Set the transport config
		TransportConfig c = new TransportConfig();
		c.put("foo", "foo");
		c.put("bar", "bar");
		db.mergeConfig(txn, transportId, c);
		assertEquals(c, db.getConfig(txn, transportId));

		// Update one of the properties and add another
		TransportConfig c1 = new TransportConfig();
		c1.put("bar", "baz");
		c1.put("bam", "bam");
		db.mergeConfig(txn, transportId, c1);
		TransportConfig merged = new TransportConfig();
		merged.put("foo", "foo");
		merged.put("bar", "baz");
		merged.put("bam", "bam");
		assertEquals(merged, db.getConfig(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTransportsNotUpdatedIfVersionIsOld() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));

		// Initialise the transport properties with version 1
		TransportProperties p = new TransportProperties(
				Collections.singletonMap("foo", "bar"));
		assertTrue(db.setRemoteProperties(txn, contactId, transportId, p, 1));
		assertEquals(Collections.singletonMap(contactId, p),
				db.getRemoteProperties(txn, transportId));

		// Replace the transport properties with version 2
		TransportProperties p1 = new TransportProperties(
				Collections.singletonMap("baz", "bam"));
		assertTrue(db.setRemoteProperties(txn, contactId, transportId, p1, 2));
		assertEquals(Collections.singletonMap(contactId, p1),
				db.getRemoteProperties(txn, transportId));

		// Try to replace the transport properties with version 1
		TransportProperties p2 = new TransportProperties(
				Collections.singletonMap("quux", "etc"));
		assertFalse(db.setRemoteProperties(txn, contactId, transportId, p2, 1));

		// Version 2 of the properties should still be there
		assertEquals(Collections.singletonMap(contactId, p1),
				db.getRemoteProperties(txn, transportId));

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
	public void testGetParentWithNoParent() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addGroup(txn, group);

		// A message with no parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, null, group, null, contentType,
				subject, timestamp, raw);
		db.addMessage(txn, child, true);
		assertTrue(db.containsMessage(txn, childId));
		assertNull(db.getParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetParentWithAbsentParent() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addGroup(txn, group);

		// A message with an absent parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, group, null,
				contentType, subject, timestamp, raw);
		db.addMessage(txn, child, true);
		assertTrue(db.containsMessage(txn, childId));
		assertFalse(db.containsMessage(txn, parentId));
		assertNull(db.getParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetParentWithParentInAnotherGroup() throws Exception {
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Another group",
				new byte[GROUP_SALT_LENGTH]);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to two groups
		db.addGroup(txn, group);
		db.addGroup(txn, group1);

		// A message with a parent in another group should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, group, null,
				contentType, subject, timestamp, raw);
		Message parent = new TestMessage(parentId, null, group1, null,
				contentType, subject, timestamp, raw);
		db.addMessage(txn, child, true);
		db.addMessage(txn, parent, true);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, parentId));
		assertNull(db.getParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetParentWithParentInSameGroup() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addGroup(txn, group);

		// A message with a parent in the same group should return the parent
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, group, null,
				contentType, subject, timestamp, raw);
		Message parent = new TestMessage(parentId, null, group, null,
				contentType, subject, timestamp, raw);
		db.addMessage(txn, child, true);
		db.addMessage(txn, parent, true);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, parentId));
		assertEquals(parentId, db.getParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageBody() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);

		// Store a couple of messages
		int bodyLength = raw.length - 20;
		Message message = new TestMessage(messageId, null, group, null,
				contentType, subject, timestamp, raw, 5, bodyLength);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, group, null,
				contentType, subject, timestamp, raw, 10, bodyLength);
		db.addMessage(txn, message, true);
		db.addMessage(txn, message1, true);

		// Calculate the expected message bodies
		byte[] expectedBody = new byte[bodyLength];
		System.arraycopy(raw, 5, expectedBody, 0, bodyLength);
		assertFalse(Arrays.equals(expectedBody, new byte[bodyLength]));
		byte[] expectedBody1 = new byte[bodyLength];
		System.arraycopy(raw, 10, expectedBody1, 0, bodyLength);
		System.arraycopy(raw, 10, expectedBody1, 0, bodyLength);

		// Retrieve the raw messages
		assertArrayEquals(raw, db.getRawMessage(txn, messageId));
		assertArrayEquals(raw, db.getRawMessage(txn, messageId1));

		// Retrieve the message bodies
		byte[] body = db.getMessageBody(txn, messageId);
		assertArrayEquals(expectedBody, body);
		byte[] body1 = db.getMessageBody(txn, messageId1);
		assertArrayEquals(expectedBody1, body1);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageHeaders() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addGroup(txn, group);

		// Store a couple of messages
		db.addMessage(txn, message, true);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		long timestamp1 = System.currentTimeMillis();
		Message message1 = new TestMessage(messageId1, parentId, group, author,
				contentType, subject, timestamp1, raw);
		db.addMessage(txn, message1, true);
		// Mark one of the messages read
		db.setReadFlag(txn, messageId, true);

		// Retrieve the message headers (order is undefined)
		Collection<MessageHeader> headers = db.getMessageHeaders(txn, groupId);
		assertEquals(2, headers.size());
		boolean firstFound = false, secondFound = false;
		for (MessageHeader header : headers) {
			if (messageId.equals(header.getId())) {
				assertHeadersMatch(message, header);
				assertTrue(header.isRead());
				firstFound = true;
			} else if (messageId1.equals(header.getId())) {
				assertHeadersMatch(message1, header);
				assertFalse(header.isRead());
				secondFound = true;
			} else {
				fail();
			}
		}
		// Both the headers should have been retrieved
		assertTrue(firstFound);
		assertTrue(secondFound);

		db.commitTransaction(txn);
		db.close();
	}

	private void assertHeadersMatch(Message m, MessageHeader h) {
		assertEquals(m.getId(), h.getId());
		if (m.getParent() == null) assertNull(h.getParent());
		else assertEquals(m.getParent(), h.getParent());
		assertEquals(m.getGroup().getId(), h.getGroupId());
		if (m.getAuthor() == null) assertNull(h.getAuthor());
		else assertEquals(m.getAuthor(), h.getAuthor());
		assertEquals(m.getContentType(), h.getContentType());
		assertEquals(m.getTimestamp(), h.getTimestamp());
	}

	@Test
	public void testAuthorStatus() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);

		// Store a message from the contact - status VERIFIED
		db.addMessage(txn, message, true);
		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		// Store a message from an unknown author - status UNKNOWN
		Author author1 = new Author(authorId1, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, group, author1,
				contentType, subject, timestamp, raw);
		db.addMessage(txn, message1, true);
		// Store an anonymous message - status ANONYMOUS
		MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		Message message2 = new TestMessage(messageId2, null, group, null,
				contentType, subject, timestamp, raw);
		db.addMessage(txn, message2, true);

		// Retrieve the message headers (order is undefined)
		Collection<MessageHeader> headers = db.getMessageHeaders(txn, groupId);
		assertEquals(3, headers.size());
		boolean firstFound = false, secondFound = false, thirdFound = false;
		for (MessageHeader header : headers) {
			if (messageId.equals(header.getId())) {
				assertHeadersMatch(message, header);
				assertEquals(Author.Status.VERIFIED, header.getAuthorStatus());
				firstFound = true;
			} else if (messageId1.equals(header.getId())) {
				assertHeadersMatch(message1, header);
				assertEquals(Author.Status.UNKNOWN, header.getAuthorStatus());
				secondFound = true;
			} else if (messageId2.equals(header.getId())) {
				assertHeadersMatch(message2, header);
				assertEquals(Author.Status.ANONYMOUS, header.getAuthorStatus());
				thirdFound = true;
			} else {
				fail();
			}
		}
		// All of the headers should have been retrieved
		assertTrue(firstFound);
		assertTrue(secondFound);
		assertTrue(thirdFound);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testReadFlag() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, true);

		// The message should be unread by default
		assertFalse(db.getReadFlag(txn, messageId));
		// Mark the message read
		db.setReadFlag(txn, messageId, true);
		// The message should be read
		assertTrue(db.getReadFlag(txn, messageId));
		// Mark the message unread
		db.setReadFlag(txn, messageId, false);
		// The message should be unread
		assertFalse(db.getReadFlag(txn, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetUnreadMessageCounts() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a couple of groups
		db.addGroup(txn, group);
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Another group",
				new byte[GROUP_SALT_LENGTH]);
		db.addGroup(txn, group1);

		// Store two messages in the first group
		db.addMessage(txn, message, true);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, group, author,
				contentType, subject, timestamp, raw);
		db.addMessage(txn, message1, true);

		// Store one message in the second group
		MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		Message message2 = new TestMessage(messageId2, null, group1, author,
				contentType, subject, timestamp, raw);
		db.addMessage(txn, message2, true);

		// Mark one of the messages in the first group read
		db.setReadFlag(txn, messageId, true);

		// There should be one unread message in each group
		Map<GroupId, Integer> counts = db.getUnreadMessageCounts(txn);
		assertEquals(2, counts.size());
		Integer count = counts.get(groupId);
		assertNotNull(count);
		assertEquals(1, count.intValue());
		count = counts.get(groupId1);
		assertNotNull(count);
		assertEquals(1, count.intValue());

		// Mark the read message unread
		db.setReadFlag(txn, messageId, false);

		// Mark the message in the second group read
		db.setReadFlag(txn, messageId2, true);

		// There should be two unread messages in the first group, none in
		// the second group
		counts = db.getUnreadMessageCounts(txn);
		assertEquals(1, counts.size());
		count = counts.get(groupId);
		assertNotNull(count);
		assertEquals(2, count.intValue());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMultipleSubscriptionsAndUnsubscriptions() throws Exception {
		// Create some groups
		List<Group> groups = new ArrayList<Group>();
		for (int i = 0; i < 100; i++) {
			GroupId id = new GroupId(TestUtils.getRandomId());
			String name = "Group " + i;
			groups.add(new Group(id, name, new byte[GROUP_SALT_LENGTH]));
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
		assertEquals(Collections.emptyList(), db.getGroups(txn));
		assertEquals(Collections.singletonList(group),
				db.getAvailableGroups(txn));

		// Subscribe to the group - it should no longer be available
		db.addGroup(txn, group);
		assertEquals(Collections.singletonList(group), db.getGroups(txn));
		assertEquals(Collections.emptyList(), db.getAvailableGroups(txn));

		// Unsubscribe from the group - it should be available again
		db.removeGroup(txn, groupId);
		assertEquals(Collections.emptyList(), db.getGroups(txn));
		assertEquals(Collections.singletonList(group),
				db.getAvailableGroups(txn));

		// The first contact unsubscribes - it should still be available
		db.setGroups(txn, contactId, Collections.<Group>emptyList(), 2);
		assertEquals(Collections.emptyList(), db.getGroups(txn));
		assertEquals(Collections.singletonList(group),
				db.getAvailableGroups(txn));

		// The second contact unsubscribes - it should no longer be available
		db.setGroups(txn, contactId1, Collections.<Group>emptyList(), 2);
		assertEquals(Collections.emptyList(), db.getGroups(txn));
		assertEquals(Collections.emptyList(), db.getAvailableGroups(txn));

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
	public void testGetInboxMessageHeaders() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and an inbox group - no headers should be returned
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addGroup(txn, group);
		db.setInboxGroup(txn, contactId, group);
		assertEquals(Collections.emptyList(),
				db.getInboxMessageHeaders(txn, contactId));

		// Add a message to the inbox group - the header should be returned
		db.addMessage(txn, message, true);
		db.addStatus(txn, contactId, messageId, false, false);
		Collection<MessageHeader> headers =
				db.getInboxMessageHeaders(txn, contactId);
		assertEquals(1, headers.size());
		MessageHeader header = headers.iterator().next();
		assertEquals(messageId, header.getId());
		assertNull(header.getParent());
		assertEquals(groupId, header.getGroupId());
		assertEquals(localAuthor, header.getAuthor());
		assertEquals(contentType, header.getContentType());
		assertEquals(timestamp, header.getTimestamp());
		assertEquals(true, header.isLocal());
		assertEquals(false, header.isRead());
		assertEquals(STORED, header.getStatus());
		assertFalse(header.isRead());

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
