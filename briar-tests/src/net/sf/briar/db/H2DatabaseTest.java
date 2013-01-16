package net.sf.briar.db;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestDatabaseConfig;
import net.sf.briar.TestMessage;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ContactTransport;
import net.sf.briar.api.transport.TemporarySecret;

import org.apache.commons.io.FileSystemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class H2DatabaseTest extends BriarTestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	private static final int MAX_SIZE = 5 * ONE_MEGABYTE;

	private final File testDir = TestUtils.getTestDirectory();
	private final Random random = new Random();
	private final Group group;
	private final AuthorId authorId;
	private final ContactId contactId;
	private final GroupId groupId;
	private final MessageId messageId, messageId1;
	private final String subject;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message, privateMessage;
	private final TransportId transportId;

	public H2DatabaseTest() throws Exception {
		super();
		authorId = new AuthorId(TestUtils.getRandomId());
		contactId = new ContactId(1);
		groupId = new GroupId(TestUtils.getRandomId());
		messageId = new MessageId(TestUtils.getRandomId());
		messageId1 = new MessageId(TestUtils.getRandomId());
		group = new Group(groupId, "Foo", null);
		subject = "Foo";
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		random.nextBytes(raw);
		message = new TestMessage(messageId, null, groupId, authorId, subject,
				timestamp, raw);
		privateMessage = new TestMessage(messageId1, null, null, null,
				subject, timestamp, raw);
		transportId = new TransportId(TestUtils.getRandomId());
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
		assertEquals(contactId, db.addContact(txn));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsSubscription(txn, groupId));
		db.addSubscription(txn, group);
		assertTrue(db.containsSubscription(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.addGroupMessage(txn, message);
		assertTrue(db.containsMessage(txn, messageId));
		assertFalse(db.containsMessage(txn, messageId1));
		db.addPrivateMessage(txn, privateMessage, contactId);
		assertTrue(db.containsMessage(txn, messageId1));
		db.commitTransaction(txn);
		db.close();

		// Check that the records are still there
		db = open(true);
		txn = db.startTransaction();
		assertTrue(db.containsContact(txn, contactId));
		assertTrue(db.containsSubscription(txn, groupId));
		assertTrue(db.containsMessage(txn, messageId));
		byte[] raw1 = db.getMessage(txn, messageId);
		assertArrayEquals(raw, raw1);
		assertTrue(db.containsMessage(txn, messageId1));
		raw1 = db.getMessage(txn, messageId1);
		assertArrayEquals(raw, raw1);
		// Delete the records
		db.removeMessage(txn, messageId);
		db.removeMessage(txn, messageId1);
		db.removeContact(txn, contactId);
		db.removeSubscription(txn, groupId);
		db.commitTransaction(txn);
		db.close();

		// Check that the records are gone
		db = open(true);
		txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(txn, transportId));
		assertFalse(db.containsSubscription(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		assertFalse(db.containsMessage(txn, messageId1));
		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContactIdsIncrease() throws Exception {
		ContactId contactId1 = new ContactId(2);
		ContactId contactId2 = new ContactId(3);
		ContactId contactId3 = new ContactId(4);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Create three contacts
		assertFalse(db.containsContact(txn, contactId));
		assertEquals(contactId, db.addContact(txn));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsContact(txn, contactId1));
		assertEquals(contactId1, db.addContact(txn));
		assertTrue(db.containsContact(txn, contactId1));
		assertFalse(db.containsContact(txn, contactId2));
		assertEquals(contactId2, db.addContact(txn));
		assertTrue(db.containsContact(txn, contactId2));
		// Delete the contact with the highest ID
		db.removeContact(txn, contactId2);
		assertFalse(db.containsContact(txn, contactId2));
		// Add another contact - a new ID should be created
		assertFalse(db.containsContact(txn, contactId3));
		assertEquals(contactId3, db.addContact(txn));
		assertTrue(db.containsContact(txn, contactId3));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRatings() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Unknown authors should be unrated
		assertEquals(Rating.UNRATED, db.getRating(txn, authorId));
		// Store a rating
		db.setRating(txn, authorId, Rating.GOOD);
		// Check that the rating was stored
		assertEquals(Rating.GOOD, db.getRating(txn, authorId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUnsubscribingRemovesGroupMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store a message
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);

		// Unsubscribing from the group should remove the message
		assertTrue(db.containsMessage(txn, messageId));
		db.removeSubscription(txn, groupId);
		assertFalse(db.containsMessage(txn, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRemovingContactRemovesPrivateMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and store a private message
		assertEquals(contactId, db.addContact(txn));
		db.addPrivateMessage(txn, privateMessage, contactId);

		// Removing the contact should remove the message
		assertTrue(db.containsMessage(txn, messageId1));
		db.removeContact(txn, contactId);
		assertFalse(db.containsMessage(txn, messageId1));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendablePrivateMessagesMustHaveStatusNew()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and store a private message
		assertEquals(contactId, db.addContact(txn));
		db.addPrivateMessage(txn, privateMessage, contactId);

		// The message has no status yet, so it should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the status to NEW should make the message sendable
		db.setStatus(txn, contactId, messageId1, Status.NEW);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId1, it.next());
		assertFalse(it.hasNext());

		// Changing the status to SENT should make the message unsendable
		db.setStatus(txn, contactId, messageId1, Status.SENT);
		assertFalse(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the status to SEEN should also make the message unsendable
		db.setStatus(txn, contactId, messageId1, Status.SEEN);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendablePrivateMessagesMustFitCapacity()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and store a private message
		assertEquals(contactId, db.addContact(txn));
		db.addPrivateMessage(txn, privateMessage, contactId);
		db.setStatus(txn, contactId, messageId1, Status.NEW);

		// The message is sendable, but too large to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, size - 1).iterator();
		assertFalse(it.hasNext());

		// The message is just the right size to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId1, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustHavePositiveSendability()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The message should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the sendability to > 0 should make the message sendable
		db.setSendability(txn, messageId, 1);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// Changing the sendability to 0 should make the message unsendable
		db.setSendability(txn, messageId, 0);
		assertFalse(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustHaveStatusNew()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);

		// The message has no status yet, so it should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the status to Status.NEW should make the message sendable
		db.setStatus(txn, contactId, messageId, Status.NEW);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// Changing the status to SENT should make the message unsendable
		db.setStatus(txn, contactId, messageId, Status.SENT);
		assertFalse(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the status to SEEN should also make the message unsendable
		db.setStatus(txn, contactId, messageId, Status.SEEN);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustBeSubscribed() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The contact is not subscribed, so the message should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// The contact subscribing should make the message sendable
		db.addSubscription(txn, contactId, group, 0L);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// The contact unsubscribing should make the message unsendable
		db.removeSubscriptions(txn, contactId, null, null);
		assertFalse(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustBeNewerThanSubscriptions()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The message is older than the contact's subscription, so it should
		// not be sendable
		db.addSubscription(txn, contactId, group, timestamp + 1);
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the contact's subscription should make the message sendable
		db.removeSubscriptions(txn, contactId, null, null);
		db.addSubscription(txn, contactId, group, timestamp);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustFitCapacity() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The message is sendable, but too large to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, size - 1).iterator();
		assertFalse(it.hasNext());

		// The message is just the right size to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustBeVisible() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The subscription is not visible to the contact, so the message
		// should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Making the subscription visible should make the message sendable
		db.addVisibility(txn, contactId, groupId);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
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

		// Add a contact and some messages to ack
		assertEquals(contactId, db.addContact(txn));
		db.addMessageToAck(txn, contactId, messageId);
		db.addMessageToAck(txn, contactId, messageId1);

		// Both message IDs should be returned
		Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		assertEquals(ids, db.getMessagesToAck(txn, contactId, 1234));

		// Remove both message IDs
		db.removeMessagesToAck(txn, contactId, ids);

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

		// Add a contact and receive the same message twice
		assertEquals(contactId, db.addContact(txn));
		db.addMessageToAck(txn, contactId, messageId);
		db.addMessageToAck(txn, contactId, messageId);

		// The message ID should only be returned once
		Collection<MessageId> ids = db.getMessagesToAck(txn, contactId, 1234);
		assertEquals(Collections.singletonList(messageId), ids);

		// Remove the message ID
		db.removeMessagesToAck(txn, contactId,
				Collections.singletonList(messageId));

		// The message ID should have been removed
		assertEquals(Collections.emptyList(), db.getMessagesToAck(txn,
				contactId, 1234));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRemoveAckedMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// Retrieve the message from the database and mark it as sent
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		db.setStatus(txn, contactId, messageId, Status.SENT);
		db.addOutstandingMessages(txn, contactId,
				Collections.singletonList(messageId));

		// The message should no longer be sendable
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Pretend that the message was acked
		db.removeAckedMessages(txn, contactId,
				Collections.singletonList(messageId));

		// The message still should not be sendable
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRemoveLostMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// Get the message and mark it as sent
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		db.setStatus(txn, contactId, messageId, Status.SENT);
		db.addOutstandingMessages(txn, contactId,
				Collections.singletonList(messageId));

		// The message should no longer be sendable
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Pretend that the message was lost
		db.removeLostMessages(txn, contactId,
				Collections.singletonList(messageId));

		// The message should be sendable again
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessagesByAuthor() throws Exception {
		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, groupId, authorId1,
				subject, timestamp, raw);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store two messages
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);
		db.addGroupMessage(txn, message1);

		// Check that each message is retrievable via its author
		Iterator<MessageId> it =
				db.getMessagesByAuthor(txn, authorId).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		it = db.getMessagesByAuthor(txn, authorId1).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId1, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetNumberOfSendableChildren() throws Exception {
		MessageId childId1 = new MessageId(TestUtils.getRandomId());
		MessageId childId2 = new MessageId(TestUtils.getRandomId());
		MessageId childId3 = new MessageId(TestUtils.getRandomId());
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Another group name",
				null);
		Message child1 = new TestMessage(childId1, messageId, groupId,
				authorId, subject, timestamp, raw);
		Message child2 = new TestMessage(childId2, messageId, groupId,
				authorId, subject, timestamp, raw);
		// The third child is in a different group
		Message child3 = new TestMessage(childId3, messageId, groupId1,
				authorId, subject, timestamp, raw);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to the groups and store the messages
		db.addSubscription(txn, group);
		db.addSubscription(txn, group1);
		db.addGroupMessage(txn, message);
		db.addGroupMessage(txn, child1);
		db.addGroupMessage(txn, child2);
		db.addGroupMessage(txn, child3);
		// Make all the children sendable
		db.setSendability(txn, childId1, 1);
		db.setSendability(txn, childId2, 5);
		db.setSendability(txn, childId3, 3);

		// There should be two sendable children
		assertEquals(2, db.getNumberOfSendableChildren(txn, messageId));
		// Make one of the children unsendable
		db.setSendability(txn, childId1, 0);
		// Now there should be one sendable child
		assertEquals(1, db.getNumberOfSendableChildren(txn, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetOldMessages() throws Exception {
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, groupId, authorId,
				subject, timestamp + 1000, raw);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store two messages
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);
		db.addGroupMessage(txn, message1);

		// Allowing enough capacity for one message should return the older one
		Iterator<MessageId> it = db.getOldMessages(txn, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// Allowing enough capacity for both messages should return both
		Collection<MessageId> ids = new HashSet<MessageId>();
		for(MessageId id : db.getOldMessages(txn, size * 2)) ids.add(id);
		assertEquals(2, ids.size());
		assertTrue(ids.contains(messageId));
		assertTrue(ids.contains(messageId1));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetFreeSpace() throws Exception {
		byte[] largeBody = new byte[ONE_MEGABYTE];
		for(int i = 0; i < largeBody.length; i++) largeBody[i] = (byte) i;
		Message message1 = new TestMessage(messageId, null, groupId, authorId,
				subject, timestamp, largeBody);
		Database<Connection> db = open(false);

		// Sanity check: there should be enough space on disk for this test
		String path = testDir.getAbsolutePath();
		assertTrue(FileSystemUtils.freeSpaceKb(path) * 1024L > MAX_SIZE);

		// The free space should not be more than the allowed maximum size
		long free = db.getFreeSpace();
		assertTrue(free <= MAX_SIZE);
		assertTrue(free > 0);

		// Storing a message should reduce the free space
		Connection txn = db.startTransaction();
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message1);
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
			public void run() {
				try {
					closing.countDown();
					db.close();
					if(!transactionFinished.get()) error.set(true);
					closed.countDown();
				} catch(Exception e) {
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
			public void run() {
				try {
					closing.countDown();
					db.close();
					if(!transactionFinished.get()) error.set(true);
					closed.countDown();
				} catch(Exception e) {
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
	public void testUpdateTransportProperties() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with a transport
		TransportProperties properties =
				new TransportProperties(Collections.singletonMap("foo", "bar"));
		Transport transport = new Transport(transportId, properties);
		assertEquals(contactId, db.addContact(txn));
		db.setTransports(txn, contactId,
				Collections.singletonList(transport), 1);
		assertEquals(Collections.singletonMap(contactId, properties),
				db.getRemoteProperties(txn, transportId));

		// Replace the transport properties
		TransportProperties properties1 =
				new TransportProperties(Collections.singletonMap("baz", "bam"));
		Transport transport1 = new Transport(transportId, properties1);
		db.setTransports(txn, contactId,
				Collections.singletonList(transport1), 2);
		assertEquals(Collections.singletonMap(contactId, properties1),
				db.getRemoteProperties(txn, transportId));

		// Remove the transport properties
		TransportProperties properties2 = new TransportProperties();
		Transport transport2 = new Transport(transportId, properties2);
		db.setTransports(txn, contactId,
				Collections.singletonList(transport2), 3);
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testLocalTransports() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Set the transport properties
		TransportProperties properties = new TransportProperties();
		properties.put("foo", "foo");
		properties.put("bar", "bar");
		db.mergeLocalProperties(txn, transportId, properties);
		Transport transport = new Transport(transportId, properties);
		assertEquals(Collections.singletonList(transport),
				db.getLocalTransports(txn));

		// Update one of the properties and add another
		TransportProperties properties1 = new TransportProperties();
		properties1.put("bar", "baz");
		properties1.put("bam", "bam");
		db.mergeLocalProperties(txn, transportId, properties1);
		TransportProperties merged = new TransportProperties();
		merged.put("foo", "foo");
		merged.put("bar", "baz");
		merged.put("bam", "bam");
		Transport transport1 = new Transport(transportId, merged);
		assertEquals(Collections.singletonList(transport1),
				db.getLocalTransports(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUpdateTransportConfig() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Set the transport config
		TransportConfig config = new TransportConfig();
		config.put("foo", "foo");
		config.put("bar", "bar");
		db.mergeConfig(txn, transportId, config);
		assertEquals(config, db.getConfig(txn, transportId));

		// Update one of the properties and add another
		TransportConfig config1 = new TransportConfig();
		config1.put("bar", "baz");
		config1.put("bam", "bam");
		db.mergeConfig(txn, transportId, config1);
		TransportConfig expected = new TransportConfig();
		expected.put("foo", "foo");
		expected.put("bar", "baz");
		expected.put("bam", "bam");
		assertEquals(expected, db.getConfig(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTransportsNotUpdatedIfTimestampIsOld() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with a transport
		TransportProperties properties =
				new TransportProperties(Collections.singletonMap("foo", "bar"));
		Transport transport = new Transport(transportId, properties);
		assertEquals(contactId, db.addContact(txn));
		db.setTransports(txn, contactId,
				Collections.singletonList(transport), 1);
		assertEquals(Collections.singletonMap(contactId, properties),
				db.getRemoteProperties(txn, transportId));

		// Replace the transport properties using a timestamp of 2
		TransportProperties properties1 =
				new TransportProperties(Collections.singletonMap("baz", "bam"));
		Transport transport1 = new Transport(transportId, properties1);
		db.setTransports(txn, contactId,
				Collections.singletonList(transport1), 2);
		assertEquals(Collections.singletonMap(contactId, properties1),
				db.getRemoteProperties(txn, transportId));

		// Try to replace the transport properties using a timestamp of 1
		TransportProperties properties2 =
				new TransportProperties(Collections.singletonMap("quux", "etc"));
		Transport transport2 = new Transport(transportId, properties2);
		db.setTransports(txn, contactId,
				Collections.singletonList(transport2), 1);

		// The old properties should still be there
		assertEquals(Collections.singletonMap(contactId, properties1),
				db.getRemoteProperties(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfNotInDatabase()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addSubscription(txn, contactId, group, 0L);

		// The message is not in the database
		assertNull(db.getMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfSeen() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);

		// Set the sendability to > 0 and the status to SEEN
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.SEEN);

		// The message is not sendable because its status is SEEN
		assertNull(db.getMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfNotSendable()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);

		// Set the sendability to 0 and the status to NEW
		db.setSendability(txn, messageId, 0);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The message is not sendable because its sendability is 0
		assertNull(db.getMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfOld() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message -
		// the message is older than the contact's subscription
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, timestamp + 1);
		db.addGroupMessage(txn, message);

		// Set the sendability to > 0 and the status to NEW
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The message is not sendable because it's too old
		assertNull(db.getMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);

		// Set the sendability to > 0 and the status to NEW
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The message is sendable so it should be returned
		byte[] b = db.getMessageIfSendable(txn, contactId, messageId);
		assertArrayEquals(raw, b);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleRequiresMessageInDatabase()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);

		// The message is not in the database
		assertFalse(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleRequiresLocalSubscription()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with a subscription
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, contactId, group, 0L);

		// There's no local subscription for the group
		assertFalse(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleRequiresContactSubscription()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// There's no contact subscription for the group
		assertFalse(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleRequiresVisibility()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);
		db.addSubscription(txn, contactId, group, 0L);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The subscription is not visible
		assertFalse(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleReturnsTrueIfAlreadySeen()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);

		// The message has already been seen by the contact
		db.setStatus(txn, contactId, messageId, Status.SEEN);

		assertTrue(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleReturnsTrueIfNew()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addSubscription(txn, contactId, group, 0L);
		db.addGroupMessage(txn, message);

		// The message has not been seen by the contact
		db.setStatus(txn, contactId, messageId, Status.NEW);

		assertTrue(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testVisibility() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);
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
	public void testGetGroupMessageParentWithNoParent() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addSubscription(txn, group);

		// A message with no parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, null, groupId, null, subject,
				timestamp, raw);
		db.addGroupMessage(txn, child);
		assertTrue(db.containsMessage(txn, childId));
		assertNull(db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithAbsentParent() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addSubscription(txn, group);

		// A message with an absent parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, groupId, null,
				subject, timestamp, raw);
		db.addGroupMessage(txn, child);
		assertTrue(db.containsMessage(txn, childId));
		assertFalse(db.containsMessage(txn, parentId));
		assertNull(db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithParentInAnotherGroup()
			throws Exception {
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Group name", null);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to two groups
		db.addSubscription(txn, group);
		db.addSubscription(txn, group1);

		// A message with a parent in another group should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, groupId, null,
				subject, timestamp, raw);
		Message parent = new TestMessage(parentId, null, groupId1, null,
				subject, timestamp, raw);
		db.addGroupMessage(txn, child);
		db.addGroupMessage(txn, parent);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, parentId));
		assertNull(db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithPrivateParent() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);

		// A message with a private parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, messageId1, groupId,
				null, subject, timestamp, raw);
		db.addGroupMessage(txn, child);
		db.addPrivateMessage(txn, privateMessage, contactId);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, messageId1));
		assertNull(db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithParentInSameGroup()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addSubscription(txn, group);

		// A message with a parent in the same group should return the parent
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, groupId, null,
				subject, timestamp, raw);
		Message parent = new TestMessage(parentId, null, groupId, null,
				subject, timestamp, raw);
		db.addGroupMessage(txn, child);
		db.addGroupMessage(txn, parent);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, parentId));
		assertEquals(parentId, db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageBody() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		assertEquals(contactId, db.addContact(txn));
		db.addSubscription(txn, group);

		// Store a couple of messages
		int bodyLength = raw.length - 20;
		Message message1 = new TestMessage(messageId, null, groupId, null,
				subject, timestamp, raw, 5, bodyLength);
		Message privateMessage1 = new TestMessage(messageId1, null, null,
				null, subject, timestamp, raw, 10, bodyLength);
		db.addGroupMessage(txn, message1);
		db.addPrivateMessage(txn, privateMessage1, contactId);

		// Calculate the expected message bodies
		byte[] expectedBody = new byte[bodyLength];
		System.arraycopy(raw, 5, expectedBody, 0, bodyLength);
		assertFalse(Arrays.equals(expectedBody, new byte[bodyLength]));
		byte[] expectedBody1 = new byte[bodyLength];
		System.arraycopy(raw, 10, expectedBody1, 0, bodyLength);
		System.arraycopy(raw, 10, expectedBody1, 0, bodyLength);

		// Retrieve the raw messages
		assertArrayEquals(raw, db.getMessage(txn, messageId));
		assertArrayEquals(raw, db.getMessage(txn, messageId1));

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
		db.addSubscription(txn, group);

		// Store a couple of messages
		db.addGroupMessage(txn, message);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		long timestamp1 = System.currentTimeMillis();
		Message message1 = new TestMessage(messageId1, parentId, groupId,
				authorId, subject, timestamp1, raw);
		db.addGroupMessage(txn, message1);
		// Mark one of the messages read
		assertFalse(db.setRead(txn, messageId, true));

		// Retrieve the message headers
		Collection<MessageHeader> headers = db.getMessageHeaders(txn, groupId);
		Iterator<MessageHeader> it = headers.iterator();
		boolean messageFound = false, message1Found = false;
		// First header (order is undefined)
		assertTrue(it.hasNext());
		MessageHeader header = it.next();
		if(messageId.equals(header.getId())) {
			assertHeadersMatch(message, header);
			assertTrue(header.getRead());
			assertFalse(header.getStarred());
			messageFound = true;
		} else if(messageId1.equals(header.getId())) {
			assertHeadersMatch(message1, header);
			assertFalse(header.getRead());
			assertFalse(header.getStarred());
			message1Found = true;
		} else {
			fail();
		}
		// Second header
		assertTrue(it.hasNext());
		header = it.next();
		if(messageId.equals(header.getId())) {
			assertHeadersMatch(message, header);
			assertTrue(header.getRead());
			assertFalse(header.getStarred());
			messageFound = true;
		} else if(messageId1.equals(header.getId())) {
			assertHeadersMatch(message1, header);
			assertFalse(header.getRead());
			assertFalse(header.getStarred());
			message1Found = true;
		} else {
			fail();
		}
		// No more headers
		assertFalse(it.hasNext());
		assertTrue(messageFound);
		assertTrue(message1Found);

		db.commitTransaction(txn);
		db.close();
	}

	private void assertHeadersMatch(Message m, MessageHeader h) {
		assertEquals(m.getId(), h.getId());
		if(m.getParent() == null) assertNull(h.getParent());
		else assertEquals(m.getParent(), h.getParent());
		if(m.getGroup() == null) assertNull(h.getGroup());
		else assertEquals(m.getGroup(), h.getGroup());
		if(m.getAuthor() == null) assertNull(h.getAuthor());
		else assertEquals(m.getAuthor(), h.getAuthor());
		assertEquals(m.getSubject(), h.getSubject());
		assertEquals(m.getTimestamp(), h.getTimestamp());
	}

	@Test
	public void testReadFlag() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store a message
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);

		// The message should be unread by default
		assertFalse(db.getRead(txn, messageId));
		// Marking the message read should return the old value
		assertFalse(db.setRead(txn, messageId, true));
		assertTrue(db.setRead(txn, messageId, true));
		// The message should be read
		assertTrue(db.getRead(txn, messageId));
		// Marking the message unread should return the old value
		assertTrue(db.setRead(txn, messageId, false));
		assertFalse(db.setRead(txn, messageId, false));
		// Unsubscribe from the group
		db.removeSubscription(txn, groupId);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testStarredFlag() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store a message
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);

		// The message should be unstarred by default
		assertFalse(db.getStarred(txn, messageId));
		// Starring the message should return the old value
		assertFalse(db.setStarred(txn, messageId, true));
		assertTrue(db.setStarred(txn, messageId, true));
		// The message should be starred
		assertTrue(db.getStarred(txn, messageId));
		// Unstarring the message should return the old value
		assertTrue(db.setStarred(txn, messageId, false));
		assertFalse(db.setStarred(txn, messageId, false));
		// Unsubscribe from the group
		db.removeSubscription(txn, groupId);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetUnreadMessageCounts() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a couple of groups
		db.addSubscription(txn, group);
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Another group", null);
		db.addSubscription(txn, group1);

		// Store two messages in the first group
		db.addGroupMessage(txn, message);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, groupId,
				authorId, subject, timestamp, raw);
		db.addGroupMessage(txn, message1);

		// Store one message in the second group
		MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		Message message2 = new TestMessage(messageId2, null, groupId1,
				authorId, subject, timestamp, raw);
		db.addGroupMessage(txn, message2);

		// Mark one of the messages in the first group read
		assertFalse(db.setRead(txn, messageId, true));

		// There should be one unread message in each group
		Map<GroupId, Integer> counts = db.getUnreadMessageCounts(txn);
		assertEquals(2, counts.size());
		Integer count = counts.get(groupId);
		assertNotNull(count);
		assertEquals(1, count.intValue());
		count = counts.get(groupId1);
		assertNotNull(count);
		assertEquals(1, count.intValue());

		// Mark the read message unread (it will now be false rather than null)
		assertTrue(db.setRead(txn, messageId, false));

		// Mark the message in the second group read
		assertFalse(db.setRead(txn, messageId2, true));

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
		for(int i = 0; i < 100; i++) {
			GroupId id = new GroupId(TestUtils.getRandomId());
			groups.add(new Group(id, "Group name", null));
		}

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to the groups and add a contact
		for(Group g : groups) db.addSubscription(txn, g);
		assertEquals(contactId, db.addContact(txn));

		// Make the groups visible to the contact
		Collections.shuffle(groups);
		for(Group g : groups) db.addVisibility(txn, contactId, g.getId());

		// Make some of the groups invisible to the contact and remove them all
		Collections.shuffle(groups);
		for(Group g : groups) {
			if(Math.random() < 0.5)
				db.removeVisibility(txn, contactId, g.getId());
			db.removeSubscription(txn, g.getId());
		}

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTemporarySecrets() throws Exception {
		// Create a contact transport and three consecutive temporary secrets
		long epoch = 123L, clockDiff = 234L, latency = 345L;
		boolean alice = false;
		long outgoing1 = 456L, centre1 = 567L;
		long outgoing2 = 678L, centre2 = 789L;
		long outgoing3 = 890L, centre3 = 901L;
		ContactTransport ct = new ContactTransport(contactId, transportId,
				epoch, clockDiff, latency, alice);
		Random random = new Random();
		byte[] secret1 = new byte[32], bitmap1 = new byte[4];
		random.nextBytes(secret1);
		random.nextBytes(bitmap1);
		TemporarySecret s1 = new TemporarySecret(contactId, transportId, epoch,
				clockDiff, latency, alice, 0L, secret1, outgoing1, centre1,
				bitmap1);
		byte[] secret2 = new byte[32], bitmap2 = new byte[4];
		random.nextBytes(secret2);
		random.nextBytes(bitmap2);
		TemporarySecret s2 = new TemporarySecret(contactId, transportId, epoch,
				clockDiff, latency, alice, 1L, secret2, outgoing2, centre2,
				bitmap2);
		byte[] secret3 = new byte[32], bitmap3 = new byte[4];
		random.nextBytes(secret3);
		random.nextBytes(bitmap3);
		TemporarySecret s3 = new TemporarySecret(contactId, transportId, epoch,
				clockDiff, latency, alice, 2L, secret3, outgoing3, centre3,
				bitmap3);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Initially there should be no secrets in the database
		assertEquals(Collections.emptyList(), db.getSecrets(txn));

		// Add the contact transport and the first two secrets
		assertEquals(contactId, db.addContact(txn));
		db.addContactTransport(txn, ct);
		db.addSecrets(txn, Arrays.asList(s1, s2));

		// Retrieve the first two secrets
		Collection<TemporarySecret> secrets = db.getSecrets(txn);
		assertEquals(2, secrets.size());
		boolean foundFirst = false, foundSecond = false;
		for(TemporarySecret s : secrets) {
			assertEquals(contactId, s.getContactId());
			assertEquals(transportId, s.getTransportId());
			assertEquals(epoch, s.getEpoch());
			assertEquals(clockDiff, s.getClockDifference());
			assertEquals(latency, s.getLatency());
			assertEquals(alice, s.getAlice());
			if(s.getPeriod() == 0L) {
				assertArrayEquals(secret1, s.getSecret());
				assertEquals(outgoing1, s.getOutgoingConnectionCounter());
				assertEquals(centre1, s.getWindowCentre());
				assertArrayEquals(bitmap1, s.getWindowBitmap());
				foundFirst = true;
			} else if(s.getPeriod() == 1L) {
				assertArrayEquals(secret2, s.getSecret());
				assertEquals(outgoing2, s.getOutgoingConnectionCounter());
				assertEquals(centre2, s.getWindowCentre());
				assertArrayEquals(bitmap2, s.getWindowBitmap());
				foundSecond = true;
			} else {
				fail();
			}
		}
		assertTrue(foundFirst);
		assertTrue(foundSecond);

		// Adding the third secret (period 2) should delete the first (period 0)
		db.addSecrets(txn, Arrays.asList(s3));
		secrets = db.getSecrets(txn);
		assertEquals(2, secrets.size());
		foundSecond = false;
		boolean foundThird = false;
		for(TemporarySecret s : secrets) {
			assertEquals(contactId, s.getContactId());
			assertEquals(transportId, s.getTransportId());
			assertEquals(epoch, s.getEpoch());
			assertEquals(clockDiff, s.getClockDifference());
			assertEquals(latency, s.getLatency());
			assertEquals(alice, s.getAlice());
			if(s.getPeriod() == 1L) {
				assertArrayEquals(secret2, s.getSecret());
				assertEquals(outgoing2, s.getOutgoingConnectionCounter());
				assertEquals(centre2, s.getWindowCentre());
				assertArrayEquals(bitmap2, s.getWindowBitmap());
				foundSecond = true;
			} else if(s.getPeriod() == 2L) {
				assertArrayEquals(secret3, s.getSecret());
				assertEquals(outgoing3, s.getOutgoingConnectionCounter());
				assertEquals(centre3, s.getWindowCentre());
				assertArrayEquals(bitmap3, s.getWindowBitmap());
				foundThird = true;
			} else {
				fail();
			}
		}
		assertTrue(foundSecond);
		assertTrue(foundThird);

		// Removing the contact should remove the secrets
		db.removeContact(txn, contactId);
		assertEquals(Collections.emptyList(), db.getSecrets(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testIncrementConnectionCounter() throws Exception {
		// Create a contact transport and a temporary secret
		long epoch = 123L, clockDiff = 234L, latency = 345L;
		boolean alice = false;
		long period = 456L, outgoing = 567L, centre = 678L;
		ContactTransport ct = new ContactTransport(contactId, transportId,
				epoch, clockDiff, latency, alice);
		Random random = new Random();
		byte[] secret = new byte[32], bitmap = new byte[4];
		random.nextBytes(secret);
		TemporarySecret s = new TemporarySecret(contactId, transportId, epoch,
				clockDiff, latency, alice, period, secret, outgoing, centre,
				bitmap);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact transport and the temporary secret
		assertEquals(contactId, db.addContact(txn));
		db.addContactTransport(txn, ct);
		db.addSecrets(txn, Arrays.asList(s));

		// Retrieve the secret
		Collection<TemporarySecret> secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		// Increment the connection counter twice and retrieve the secret again
		assertEquals(outgoing, db.incrementConnectionCounter(txn,
				s.getContactId(), s.getTransportId(), s.getPeriod()));
		assertEquals(outgoing + 1L, db.incrementConnectionCounter(txn,
				s.getContactId(), s.getTransportId(), s.getPeriod()));
		secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing + 2L, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetConnectionWindow() throws Exception {
		// Create a contact transport and a temporary secret
		long epoch = 123L, clockDiff = 234L, latency = 345L;
		boolean alice = false;
		long period = 456L, outgoing = 567L, centre = 678L;
		ContactTransport ct = new ContactTransport(contactId, transportId,
				epoch, clockDiff, latency, alice);
		Random random = new Random();
		byte[] secret = new byte[32], bitmap = new byte[4];
		random.nextBytes(secret);
		TemporarySecret s = new TemporarySecret(contactId, transportId, epoch,
				clockDiff, latency, alice, period, secret, outgoing, centre,
				bitmap);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact transport and the temporary secret
		assertEquals(contactId, db.addContact(txn));
		db.addContactTransport(txn, ct);
		db.addSecrets(txn, Arrays.asList(s));

		// Retrieve the secret
		Collection<TemporarySecret> secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		// Update the connection window and retrieve the secret again
		random.nextBytes(bitmap);
		db.setConnectionWindow(txn, contactId, transportId, period, centre,
				bitmap);
		secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		// Updating a nonexistent window should not throw an exception
		db.setConnectionWindow(txn, contactId, transportId, period + 1L, 1L,
				bitmap);
		// The nonexistent window should not have been created
		secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContactTransports() throws Exception {
		// Create some contact transports
		long epoch1 = 123L, clockDiff1 = 234L, latency1 = 345L;
		long epoch2 = 456L, clockDiff2 = 567L, latency2 = 678L;
		boolean alice1 = true, alice2 = false;
		TransportId transportId1 = new TransportId(TestUtils.getRandomId());
		TransportId transportId2 = new TransportId(TestUtils.getRandomId());
		ContactTransport ct1 = new ContactTransport(contactId, transportId1,
				epoch1, clockDiff1, latency1, alice1);
		ContactTransport ct2 = new ContactTransport(contactId, transportId2,
				epoch2, clockDiff2, latency2, alice2);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Initially there should be no contact transports in the database
		assertEquals(Collections.emptyList(), db.getContactTransports(txn));

		// Add a contact and the contact transports
		assertEquals(contactId, db.addContact(txn));
		db.addContactTransport(txn, ct1);
		db.addContactTransport(txn, ct2);

		// Retrieve the contact transports
		Collection<ContactTransport> cts = db.getContactTransports(txn);
		assertEquals(2, cts.size());
		boolean foundFirst = false, foundSecond = false;
		for(ContactTransport ct : cts) {
			assertEquals(contactId, ct.getContactId());
			if(ct.getTransportId().equals(transportId1)) {
				assertEquals(epoch1, ct.getEpoch());
				assertEquals(clockDiff1, ct.getClockDifference());
				assertEquals(latency1, ct.getLatency());
				assertEquals(alice1, ct.getAlice());
				foundFirst = true;
			} else if(ct.getTransportId().equals(transportId2)) {
				assertEquals(epoch2, ct.getEpoch());
				assertEquals(clockDiff2, ct.getClockDifference());
				assertEquals(latency2, ct.getLatency());
				assertEquals(alice2, ct.getAlice());
				foundSecond = true;
			} else {
				fail();
			}
		}
		assertTrue(foundFirst);
		assertTrue(foundSecond);

		// Removing the contact should remove the contact transports
		db.removeContact(txn, contactId);
		assertEquals(Collections.emptyList(), db.getContactTransports(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testExceptionHandling() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();
		try {
			// Ask for a nonexistent message - an exception should be thrown
			db.getMessage(txn, messageId);
			fail();
		} catch(DbException expected) {
			// It should be possible to abort the transaction without error
			db.abortTransaction(txn);
		}
		// It should be possible to close the database cleanly
		db.close();
	}

	private Database<Connection> open(boolean resume) throws Exception {
		Database<Connection> db = new H2Database(new SystemClock(),
				new TestDatabaseConfig(testDir, MAX_SIZE));
		db.open(resume);
		return db;
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
