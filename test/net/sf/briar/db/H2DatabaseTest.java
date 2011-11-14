package net.sf.briar.db;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;
import net.sf.briar.TestDatabaseModule;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.crypto.Password;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.api.transport.ConnectionWindowFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.protocol.writers.ProtocolWritersModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;
import net.sf.briar.transport.batch.TransportBatchModule;
import net.sf.briar.transport.stream.TransportStreamModule;

import org.apache.commons.io.FileSystemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class H2DatabaseTest extends TestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	private static final int MAX_SIZE = 5 * ONE_MEGABYTE;

	private final File testDir = TestUtils.getTestDirectory();
	// The password has the format <file password> <space> <user password>
	private final String passwordString = "foo bar";
	private final Password password = new TestPassword();
	private final Random random = new Random();
	private final ConnectionWindowFactory connectionWindowFactory;
	private final GroupFactory groupFactory;

	private final AuthorId authorId;
	private final BatchId batchId;
	private final ContactId contactId;
	private final GroupId groupId;
	private final MessageId messageId, privateMessageId;
	private final String subject;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message, privateMessage;
	private final Group group;
	private final TransportId transportId;
	private final TransportIndex localIndex, remoteIndex;
	private final TransportProperties properties;
	private final Map<ContactId, TransportProperties> remoteProperties;
	private final Collection<Transport> remoteTransports;
	private final Map<Group, Long> subscriptions;
	private final byte[] secret;

	public H2DatabaseTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new DatabaseModule(), new ProtocolModule(),
				new ProtocolWritersModule(), new SerialModule(),
				new TransportBatchModule(), new TransportModule(),
				new TransportStreamModule(), new TestDatabaseModule(testDir));
		connectionWindowFactory = i.getInstance(ConnectionWindowFactory.class);
		groupFactory = i.getInstance(GroupFactory.class);
		authorId = new AuthorId(TestUtils.getRandomId());
		batchId = new BatchId(TestUtils.getRandomId());
		contactId = new ContactId(1);
		groupId = new GroupId(TestUtils.getRandomId());
		messageId = new MessageId(TestUtils.getRandomId());
		privateMessageId = new MessageId(TestUtils.getRandomId());
		subject = "Foo";
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		random.nextBytes(raw);
		message = new TestMessage(messageId, null, groupId, authorId, subject,
				timestamp, raw);
		privateMessage = new TestMessage(privateMessageId, null, null, null,
				subject, timestamp, raw);
		group = groupFactory.createGroup(groupId, "Group name", null);
		transportId = new TransportId(TestUtils.getRandomId());
		localIndex = new TransportIndex(1);
		remoteIndex = new TransportIndex(13);
		properties = new TransportProperties(
				Collections.singletonMap("foo", "bar"));
		remoteProperties = Collections.singletonMap(contactId, properties);
		Transport remoteTransport = new Transport(transportId, remoteIndex,
				properties);
		remoteTransports = Collections.singletonList(remoteTransport);
		subscriptions = Collections.singletonMap(group, 0L);
		secret = new byte[123];
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
		assertEquals(contactId, db.addContact(txn, secret));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsSubscription(txn, groupId));
		db.addSubscription(txn, group);
		assertTrue(db.containsSubscription(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.addGroupMessage(txn, message);
		assertTrue(db.containsMessage(txn, messageId));
		assertFalse(db.containsMessage(txn, privateMessageId));
		db.addPrivateMessage(txn, privateMessage, contactId);
		assertTrue(db.containsMessage(txn, privateMessageId));
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
		assertTrue(db.containsMessage(txn, privateMessageId));
		raw1 = db.getMessage(txn, privateMessageId);
		assertArrayEquals(raw, raw1);
		// Delete the records
		db.removeMessage(txn, messageId);
		db.removeMessage(txn, privateMessageId);
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
		assertFalse(db.containsMessage(txn, privateMessageId));
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
		assertEquals(contactId, db.addContact(txn, secret));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsContact(txn, contactId1));
		assertEquals(contactId1, db.addContact(txn, secret));
		assertTrue(db.containsContact(txn, contactId1));
		assertFalse(db.containsContact(txn, contactId2));
		assertEquals(contactId2, db.addContact(txn, secret));
		assertTrue(db.containsContact(txn, contactId2));
		// Delete the contact with the highest ID
		db.removeContact(txn, contactId2);
		assertFalse(db.containsContact(txn, contactId2));
		// Add another contact - a new ID should be created
		assertFalse(db.containsContact(txn, contactId3));
		assertEquals(contactId3, db.addContact(txn, secret));
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addPrivateMessage(txn, privateMessage, contactId);

		// Removing the contact should remove the message
		assertTrue(db.containsMessage(txn, privateMessageId));
		db.removeContact(txn, contactId);
		assertFalse(db.containsMessage(txn, privateMessageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendablePrivateMessagesMustHaveStatusNew()
	throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and store a private message
		assertEquals(contactId, db.addContact(txn, secret));
		db.addPrivateMessage(txn, privateMessage, contactId);

		// The message has no status yet, so it should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the status to NEW should make the message sendable
		db.setStatus(txn, contactId, privateMessageId, Status.NEW);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(privateMessageId, it.next());
		assertFalse(it.hasNext());

		// Changing the status to SENT should make the message unsendable
		db.setStatus(txn, contactId, privateMessageId, Status.SENT);
		assertFalse(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the status to SEEN should also make the message unsendable
		db.setStatus(txn, contactId, privateMessageId, Status.SEEN);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addPrivateMessage(txn, privateMessage, contactId);
		db.setStatus(txn, contactId, privateMessageId, Status.NEW);

		// The message is sendable, but too large to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, size - 1).iterator();
		assertFalse(it.hasNext());

		// The message is just the right size to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(privateMessageId, it.next());
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The contact is not subscribed, so the message should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// The contact subscribing should make the message sendable
		db.setSubscriptions(txn, contactId, subscriptions, 1);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// The contact unsubscribing should make the message unsendable
		db.setSubscriptions(txn, contactId,
				Collections.<Group, Long>emptyMap(), 2);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.addGroupMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);

		// The message is older than the contact's subscription, so it should
		// not be sendable
		db.setSubscriptions(txn, contactId,
				Collections.singletonMap(group, timestamp + 1), 1);
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the contact's subscription should make the message sendable
		db.setSubscriptions(txn, contactId,
				Collections.singletonMap(group, timestamp), 2);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testBatchesToAck() throws Exception {
		BatchId batchId1 = new BatchId(TestUtils.getRandomId());
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and some batches to ack
		assertEquals(contactId, db.addContact(txn, secret));
		db.addBatchToAck(txn, contactId, batchId);
		db.addBatchToAck(txn, contactId, batchId1);

		// Both batch IDs should be returned
		Collection<BatchId> acks = db.getBatchesToAck(txn, contactId);
		assertEquals(2, acks.size());
		assertTrue(acks.contains(batchId));
		assertTrue(acks.contains(batchId1));

		// Remove the batch IDs
		db.removeBatchesToAck(txn, contactId, acks);

		// Both batch IDs should have been removed
		acks = db.getBatchesToAck(txn, contactId);
		assertEquals(0, acks.size());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testDuplicateBatchesReceived() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and receive the same batch twice
		assertEquals(contactId, db.addContact(txn, secret));
		db.addBatchToAck(txn, contactId, batchId);
		db.addBatchToAck(txn, contactId, batchId);

		// The batch ID should only be returned once
		Collection<BatchId> acks = db.getBatchesToAck(txn, contactId);
		assertEquals(1, acks.size());
		assertTrue(acks.contains(batchId));

		// Remove the batch ID
		db.removeBatchesToAck(txn, contactId, acks);

		// The batch ID should have been removed
		acks = db.getBatchesToAck(txn, contactId);
		assertEquals(0, acks.size());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSameBatchCannotBeSentTwice() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);

		// Add an outstanding batch
		db.addOutstandingBatch(txn, contactId, batchId,
				Collections.singleton(messageId));

		// It should not be possible to add the same outstanding batch again
		try {
			db.addOutstandingBatch(txn, contactId, batchId,
					Collections.singleton(messageId));
			fail();
		} catch(DbException expected) {}

		db.abortTransaction(txn);
		db.close();
	}

	@Test
	public void testSameBatchCanBeSentToDifferentContacts() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add two contacts, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn, secret));
		ContactId contactId1 = db.addContact(txn, secret);
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);

		// Add an outstanding batch for the first contact
		db.addOutstandingBatch(txn, contactId, batchId,
				Collections.singleton(messageId));

		// Add the same outstanding batch for the second contact
		db.addOutstandingBatch(txn, contactId1, batchId,
				Collections.singleton(messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRemoveAckedBatch() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		db.addOutstandingBatch(txn, contactId, batchId,
				Collections.singleton(messageId));

		// The message should no longer be sendable
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Pretend that the batch was acked
		db.removeAckedBatch(txn, contactId, batchId);

		// The message still should not be sendable
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRemoveLostBatch() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		db.addOutstandingBatch(txn, contactId, batchId,
				Collections.singleton(messageId));

		// The message should no longer be sendable
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Pretend that the batch was lost
		db.removeLostBatch(txn, contactId, batchId);

		// The message should be sendable again
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRetransmission() throws Exception {
		BatchId[] ids = new BatchId[DatabaseConstants.RETRANSMIT_THRESHOLD + 5];
		for(int i = 0; i < ids.length; i++) {
			ids[i] = new BatchId(TestUtils.getRandomId());
		}
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		assertEquals(contactId, db.addContact(txn, secret));

		// Add some outstanding batches, a few ms apart
		for(int i = 0; i < ids.length; i++) {
			db.addOutstandingBatch(txn, contactId, ids[i],
					Collections.<MessageId>emptyList());
			Thread.sleep(5);
		}

		// The contact acks the batches in reverse order. The first
		// RETRANSMIT_THRESHOLD - 1 acks should not trigger any retransmissions
		for(int i = 0; i < DatabaseConstants.RETRANSMIT_THRESHOLD - 1; i++) {
			db.removeAckedBatch(txn, contactId, ids[ids.length - i - 1]);
			Collection<BatchId> lost = db.getLostBatches(txn, contactId);
			assertEquals(Collections.emptyList(), lost);
		}

		// The next ack should trigger the retransmission of the remaining
		// five outstanding batches
		int index = ids.length - DatabaseConstants.RETRANSMIT_THRESHOLD;
		db.removeAckedBatch(txn, contactId, ids[index]);
		Collection<BatchId> lost = db.getLostBatches(txn, contactId);
		for(int i = 0; i < index; i++) {
			assertTrue(lost.contains(ids[i]));
		}

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testNoRetransmission() throws Exception {
		BatchId[] ids = new BatchId[DatabaseConstants.RETRANSMIT_THRESHOLD * 2];
		for(int i = 0; i < ids.length; i++) {
			ids[i] = new BatchId(TestUtils.getRandomId());
		}
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		assertEquals(contactId, db.addContact(txn, secret));

		// Add some outstanding batches, a few ms apart
		for(int i = 0; i < ids.length; i++) {
			db.addOutstandingBatch(txn, contactId, ids[i],
					Collections.<MessageId>emptyList());
			Thread.sleep(5);
		}

		// The contact acks the batches in the order they were sent - nothing
		// should be retransmitted
		for(int i = 0; i < ids.length; i++) {
			db.removeAckedBatch(txn, contactId, ids[i]);
			Collection<BatchId> lost = db.getLostBatches(txn, contactId);
			assertEquals(Collections.emptyList(), lost);
		}

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
		Group group1 = groupFactory.createGroup(groupId1, "Another group name",
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
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean transactionFinished = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread t = new Thread() {
			public void run() {
				try {
					db.close();
					if(!transactionFinished.get()) error.set(true);
					latch.countDown();
				} catch(Exception e) {
					error.set(true);
				}
			}
		};
		t.start();
		// Do whatever the transaction needs to do
		Thread.sleep(10);
		transactionFinished.set(true);
		// Commit the transaction
		db.commitTransaction(txn);
		// The other thread should now terminate
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testCloseWaitsForAbort() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean transactionFinished = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread t = new Thread() {
			public void run() {
				try {
					db.close();
					if(!transactionFinished.get()) error.set(true);
					latch.countDown();
				} catch(Exception e) {
					error.set(true);
				}
			}
		};
		t.start();
		// Do whatever the transaction needs to do
		Thread.sleep(10);
		transactionFinished.set(true);
		// Abort the transaction
		db.abortTransaction(txn);
		// The other thread should now terminate
		assertTrue(latch.await(5, TimeUnit.SECONDS));
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testUpdateTransportProperties() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with some transport properties
		assertEquals(contactId, db.addContact(txn, secret));
		db.setTransports(txn, contactId, remoteTransports, 1);
		assertEquals(remoteProperties,
				db.getRemoteProperties(txn, transportId));

		// Replace the transport properties
		TransportProperties properties1 =
			new TransportProperties(Collections.singletonMap("baz", "bam"));
		Transport remoteTransport1 =
			new Transport(transportId, remoteIndex, properties1);
		Collection<Transport> remoteTransports1 =
			Collections.singletonList(remoteTransport1);
		Map<ContactId, TransportProperties> remoteProperties1 =
			Collections.singletonMap(contactId, properties1);
		db.setTransports(txn, contactId, remoteTransports1, 2);
		assertEquals(remoteProperties1,
				db.getRemoteProperties(txn, transportId));

		// Remove the transport properties
		db.setTransports(txn, contactId, Collections.<Transport>emptyList(), 3);
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testLocalTransports() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Allocate a transport index
		assertEquals(localIndex, db.addTransport(txn, transportId));

		// Set the local transport properties
		db.setLocalProperties(txn, transportId, properties);
		assertEquals(Collections.singletonList(properties),
				db.getLocalTransports(txn));

		// Remove the local transport properties - the transport itself will
		// not be removed
		db.setLocalProperties(txn, transportId, new TransportProperties());
		assertEquals(Collections.singletonList(Collections.emptyMap()),
				db.getLocalTransports(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUpdateTransportConfig() throws Exception {
		TransportConfig config =
			new TransportConfig(Collections.singletonMap("foo", "bar"));
		TransportConfig config1 =
			new TransportConfig(Collections.singletonMap("baz", "bam"));

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Allocate a transport index
		assertEquals(localIndex, db.addTransport(txn, transportId));

		// Set the transport config
		db.setConfig(txn, transportId, config);
		assertEquals(config, db.getConfig(txn, transportId));

		// Update the transport config
		db.setConfig(txn, transportId, config1);
		assertEquals(config1, db.getConfig(txn, transportId));

		// Remove the transport config
		db.setConfig(txn, transportId, new TransportConfig());
		assertEquals(Collections.emptyMap(), db.getConfig(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTransportsNotUpdatedIfTimestampIsOld() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with some transport properties
		assertEquals(contactId, db.addContact(txn, secret));
		db.setTransports(txn, contactId, remoteTransports, 1);
		assertEquals(remoteProperties,
				db.getRemoteProperties(txn, transportId));

		// Replace the transport properties using a timestamp of 2
		TransportProperties properties1 =
			new TransportProperties(Collections.singletonMap("baz", "bam"));
		Transport remoteTransport1 =
			new Transport(transportId, remoteIndex, properties1);
		Collection<Transport> remoteTransports1 =
			Collections.singletonList(remoteTransport1);
		Map<ContactId, TransportProperties> remoteProperties1 =
			Collections.singletonMap(contactId, properties1);
		db.setTransports(txn, contactId, remoteTransports1, 2);
		assertEquals(remoteProperties1,
				db.getRemoteProperties(txn, transportId));

		// Try to replace the transport properties using a timestamp of 1
		TransportProperties properties2 =
			new TransportProperties(Collections.singletonMap("quux", "etc"));
		Transport remoteTransport2 =
			new Transport(transportId, remoteIndex, properties2);
		Collection<Transport> remoteTransports2 =
			Collections.singletonList(remoteTransport2);
		db.setTransports(txn, contactId, remoteTransports2, 1);

		// The old properties should still be there
		assertEquals(remoteProperties1,
				db.getRemoteProperties(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUpdateSubscriptions() throws Exception {
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = groupFactory.createGroup(groupId1, "Another group name",
				null);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with some subscriptions
		assertEquals(contactId, db.addContact(txn, secret));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
		assertEquals(Collections.singletonList(group),
				db.getSubscriptions(txn, contactId));

		// Update the subscriptions
		Map<Group, Long> subscriptions1 = Collections.singletonMap(group1, 0L);
		db.setSubscriptions(txn, contactId, subscriptions1, 2);
		assertEquals(Collections.singletonList(group1),
				db.getSubscriptions(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSubscriptionsNotUpdatedIfTimestampIsOld()
	throws Exception {
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = groupFactory.createGroup(groupId1, "Another group name",
				null);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with some subscriptions
		assertEquals(contactId, db.addContact(txn, secret));
		db.setSubscriptions(txn, contactId, subscriptions, 2);
		assertEquals(Collections.singletonList(group),
				db.getSubscriptions(txn, contactId));

		// Try to update the subscriptions using a timestamp of 1
		Map<Group, Long> subscriptions1 = Collections.singletonMap(group1, 0L);
		db.setSubscriptions(txn, contactId, subscriptions1, 1);

		// The old subscriptions should still be there
		assertEquals(Collections.singletonList(group),
				db.getSubscriptions(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfNotInDatabase()
	throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setSubscriptions(txn, contactId, subscriptions, 1);

		// The message is not in the database
		assertNull(db.getMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfSeen()
	throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		Map<Group, Long> subs = Collections.singletonMap(group, timestamp + 1);
		db.setSubscriptions(txn, contactId, subs, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);

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
		assertEquals(contactId, db.addContact(txn, secret));
		db.setSubscriptions(txn, contactId, subscriptions, 1);

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
		assertEquals(contactId, db.addContact(txn, secret));
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message);
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		db.setSubscriptions(txn, contactId, subscriptions, 1);
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
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);
		// The group should not be visible to the contact
		assertEquals(Collections.emptyList(), db.getVisibility(txn, groupId));
		// Make the group visible to the contact
		db.setVisibility(txn, groupId, Collections.singleton(contactId));
		assertEquals(Collections.singletonList(contactId),
				db.getVisibility(txn, groupId));
		// Make the group invisible again
		db.setVisibility(txn, groupId, Collections.<ContactId>emptyList());
		assertEquals(Collections.emptyList(), db.getVisibility(txn, groupId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGettingUnknownConnectionWindowReturnsDefault()
	throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		assertEquals(contactId, db.addContact(txn, secret));
		// Get the connection window for a new index
		ConnectionWindow w = db.getConnectionWindow(txn, contactId,
				remoteIndex);
		// The connection window should exist and be in the initial state
		assertNotNull(w);
		assertEquals(0L, w.getCentre());
		assertEquals(0, w.getBitmap());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testConnectionWindow() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		assertEquals(contactId, db.addContact(txn, secret));
		// Get the connection window for a new index
		ConnectionWindow w = db.getConnectionWindow(txn, contactId,
				remoteIndex);
		// The connection window should exist and be in the initial state
		assertNotNull(w);
		assertEquals(0L, w.getCentre());
		assertEquals(0, w.getBitmap());
		// Update the connection window and store it
		w.setSeen(5L);
		db.setConnectionWindow(txn, contactId, remoteIndex, w);
		// Check that the connection window was stored
		w = db.getConnectionWindow(txn, contactId, remoteIndex);
		assertNotNull(w);
		assertEquals(6L, w.getCentre());
		assertTrue(w.isSeen(5L));
		assertEquals(0x00010000, w.getBitmap());

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
		Group group1 = groupFactory.createGroup(groupId1, "Group name", null);
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
	public void testGetGroupMessageParentWithPrivateParent()
	throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);

		// A message with a private parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, privateMessageId, groupId,
				null, subject, timestamp, raw);
		db.addGroupMessage(txn, child);
		db.addPrivateMessage(txn, privateMessage, contactId);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, privateMessageId));
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
	public void testTimestamps() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		assertEquals(contactId, db.addContact(txn, secret));

		// The subscription and transport timestamps should be initialised to 0
		assertEquals(0L, db.getSubscriptionsModified(txn, contactId));
		assertEquals(0L, db.getSubscriptionsSent(txn, contactId));
		assertEquals(0L, db.getTransportsModified(txn));
		assertEquals(0L, db.getTransportsSent(txn, contactId));

		// Update the timestamps
		db.setSubscriptionsModified(txn, Collections.singleton(contactId), 1L);
		db.setSubscriptionsSent(txn, contactId, 2L);
		db.setTransportsModified(txn, 3L);
		db.setTransportsSent(txn, contactId, 4L);

		// Check that the updated values were stored
		assertEquals(1L, db.getSubscriptionsModified(txn, contactId));
		assertEquals(2L, db.getSubscriptionsSent(txn, contactId));
		assertEquals(3L, db.getTransportsModified(txn));
		assertEquals(4L, db.getTransportsSent(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageBody() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		assertEquals(contactId, db.addContact(txn, secret));
		db.addSubscription(txn, group);

		// Store a couple of messages
		int bodyLength = raw.length - 20;
		Message message1 = new TestMessage(messageId, null, groupId, null,
				subject, timestamp, raw, 5, bodyLength);
		Message privateMessage1 = new TestMessage(privateMessageId, null, null,
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
		assertArrayEquals(raw, db.getMessage(txn, privateMessageId));

		// Retrieve the message bodies
		byte[] body = db.getMessageBody(txn, messageId);
		assertArrayEquals(expectedBody, body);
		byte[] body1 = db.getMessageBody(txn, privateMessageId);
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
		Group group1 = groupFactory.createGroup(groupId1, "Another group",
				null);
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
		Database<Connection> db = new H2Database(testDir, password, MAX_SIZE,
				connectionWindowFactory, groupFactory);
		db.open(resume);
		return db;
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private class TestPassword implements Password {

		public char[] getPassword() {
			return passwordString.toCharArray();
		}
	}
}
