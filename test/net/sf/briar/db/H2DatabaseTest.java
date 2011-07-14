package net.sf.briar.db;

import java.io.File;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.crypto.Password;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

import org.apache.commons.io.FileSystemUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class H2DatabaseTest extends TestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	private static final int MAX_SIZE = 5 * ONE_MEGABYTE;

	private final File testDir = TestUtils.getTestDirectory();
	// The password has the format <file password> <space> <user password>
	private final String passwordString = "foo bar";
	private final Random random = new Random();
	private final AuthorId authorId;
	private final BatchId batchId;
	private final ContactId contactId;
	private final GroupId groupId;
	private final MessageId messageId;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message;

	public H2DatabaseTest() {
		super();
		authorId = new AuthorId(TestUtils.getRandomId());
		batchId = new BatchId(TestUtils.getRandomId());
		contactId = new ContactId(1);
		groupId = new GroupId(TestUtils.getRandomId());
		messageId = new MessageId(TestUtils.getRandomId());
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		random.nextBytes(raw);
		message = new TestMessage(messageId, MessageId.NONE, groupId, authorId,
				timestamp, raw);
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testPersistence() throws DbException {
		Database<Connection> db = open(false);

		// Store some records
		Connection txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		Map<String, String> transports = Collections.singletonMap("foo", "bar");
		assertEquals(contactId, db.addContact(txn, transports));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsSubscription(txn, groupId));
		db.addSubscription(txn, groupId);
		assertTrue(db.containsSubscription(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.addMessage(txn, message);
		assertTrue(db.containsMessage(txn, messageId));
		db.commitTransaction(txn);
		db.close();

		// Reopen the database
		db = open(true);
		// Check that the records are still there
		txn = db.startTransaction();
		assertTrue(db.containsContact(txn, contactId));
		transports = db.getTransports(txn, contactId);
		assertEquals(Collections.singletonMap("foo", "bar"), transports);
		assertTrue(db.containsSubscription(txn, groupId));
		assertTrue(db.containsMessage(txn, messageId));
		byte[] raw1 = db.getMessage(txn, messageId);
		assertTrue(Arrays.equals(raw, raw1));
		// Delete the records
		db.removeContact(txn, contactId);
		db.removeMessage(txn, messageId);
		db.removeSubscription(txn, groupId);
		db.commitTransaction(txn);
		db.close();

		// Repoen the database
		db = open(true);
		// Check that the records are gone
		txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		assertEquals(Collections.emptyMap(), db.getTransports(txn, contactId));
		assertFalse(db.containsSubscription(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContactIdsIncrease() throws DbException {
		ContactId contactId1 = new ContactId(2);
		ContactId contactId2 = new ContactId(3);
		ContactId contactId3 = new ContactId(4);
		Database<Connection> db = open(false);

		// Create three contacts
		Connection txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		assertEquals(contactId, db.addContact(txn, null));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsContact(txn, contactId1));
		assertEquals(contactId1, db.addContact(txn, null));
		assertTrue(db.containsContact(txn, contactId1));
		assertFalse(db.containsContact(txn, contactId2));
		assertEquals(contactId2, db.addContact(txn, null));
		assertTrue(db.containsContact(txn, contactId2));
		// Delete one of the contacts
		db.removeContact(txn, contactId1);
		assertFalse(db.containsContact(txn, contactId1));
		// Add another contact - a new ID should be created
		assertFalse(db.containsContact(txn, contactId3));
		assertEquals(contactId3, db.addContact(txn, null));
		assertTrue(db.containsContact(txn, contactId3));
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testRatings() throws DbException {
		Database<Connection> db = open(false);

		Connection txn = db.startTransaction();
		// Unknown authors should be unrated
		assertEquals(Rating.UNRATED, db.getRating(txn, authorId));
		// Store a rating
		db.setRating(txn, authorId, Rating.GOOD);
		db.commitTransaction(txn);
		// Check that the rating was stored
		txn = db.startTransaction();
		assertEquals(Rating.GOOD, db.getRating(txn, authorId));
		db.commitTransaction(txn);
		
		db.close();
	}

	@Test
	public void testUnsubscribingRemovesMessage() throws DbException {
		Database<Connection> db = open(false);

		// Subscribe to a group and store a message
		Connection txn = db.startTransaction();
		db.addSubscription(txn, groupId);
		db.addMessage(txn, message);
		db.commitTransaction(txn);

		// Unsubscribing from the group should delete the message
		txn = db.startTransaction();
		assertTrue(db.containsMessage(txn, messageId));
		db.removeSubscription(txn, groupId);
		assertFalse(db.containsMessage(txn, messageId));
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testSendableMessagesMustBeSendable() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact, subscribe to a group and store a message
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		db.addSubscription(txn, groupId);
		db.setSubscriptions(txn, contactId, Collections.singleton(groupId), 1);
		db.addMessage(txn, message);
		db.setStatus(txn, contactId, messageId, Status.NEW);
		db.commitTransaction(txn);

		// The message should not be sendable
		txn = db.startTransaction();
		assertEquals(0, db.getSendability(txn, messageId));
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		// Changing the sendability to > 0 should make the message sendable
		txn = db.startTransaction();
		db.setSendability(txn, messageId, 1);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		db.commitTransaction(txn);

		// Changing the sendability to 0 should make the message unsendable
		txn = db.startTransaction();
		db.setSendability(txn, messageId, 0);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testSendableMessagesMustBeNew() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact, subscribe to a group and store a message
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		db.addSubscription(txn, groupId);
		db.setSubscriptions(txn, contactId, Collections.singleton(groupId), 1);
		db.addMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.commitTransaction(txn);

		// The message has no status yet, so it should not be sendable
		txn = db.startTransaction();
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		// Changing the status to Status.NEW should make the message sendable
		txn = db.startTransaction();
		db.setStatus(txn, contactId, messageId, Status.NEW);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		db.commitTransaction(txn);

		// Changing the status to SENT should make the message unsendable
		txn = db.startTransaction();
		db.setStatus(txn, contactId, messageId, Status.SENT);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		// Changing the status to SEEN should also make the message unsendable
		txn = db.startTransaction();
		db.setStatus(txn, contactId, messageId, Status.SEEN);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testSendableMessagesMustBeSubscribed() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact, subscribe to a group and store a message
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		db.addSubscription(txn, groupId);
		db.addMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);
		db.commitTransaction(txn);

		// The contact is not subscribed, so the message should not be sendable
		txn = db.startTransaction();
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		// The contact subscribing should make the message sendable
		txn = db.startTransaction();
		db.setSubscriptions(txn, contactId, Collections.singleton(groupId), 1);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		db.commitTransaction(txn);

		// The contact unsubscribing should make the message unsendable
		txn = db.startTransaction();
		db.setSubscriptions(txn, contactId, Collections.<GroupId>emptySet(), 2);
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testSendableMessagesMustFitCapacity() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact, subscribe to a group and store a message
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		db.addSubscription(txn, groupId);
		db.setSubscriptions(txn, contactId, Collections.singleton(groupId), 1);
		db.addMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);
		db.commitTransaction(txn);

		// The message is too large to send
		txn = db.startTransaction();
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, size - 1).iterator();
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		// The message is just the right size to send
		txn = db.startTransaction();
		it = db.getSendableMessages(txn, contactId, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testBatchesToAck() throws DbException {
		BatchId batchId1 = new BatchId(TestUtils.getRandomId());
		Database<Connection> db = open(false);

		// Add a contact and some batches to ack
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		db.addBatchToAck(txn, contactId, batchId);
		db.addBatchToAck(txn, contactId, batchId1);
		db.commitTransaction(txn);

		// Both batch IDs should be returned
		txn = db.startTransaction();
		Set<BatchId> acks = db.removeBatchesToAck(txn, contactId);
		assertEquals(2, acks.size());
		assertTrue(acks.contains(batchId));
		assertTrue(acks.contains(batchId1));
		db.commitTransaction(txn);

		// Both batch IDs should have been removed
		txn = db.startTransaction();
		acks = db.removeBatchesToAck(txn, contactId);
		assertEquals(0, acks.size());
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testRemoveAckedBatch() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact, subscribe to a group and store a message
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		db.addSubscription(txn, groupId);
		db.setSubscriptions(txn, contactId, Collections.singleton(groupId), 1);
		db.addMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);
		db.commitTransaction(txn);

		// Get the message and mark it as sent
		txn = db.startTransaction();
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		db.setStatus(txn, contactId, messageId, Status.SENT);
		db.addOutstandingBatch(txn, contactId, batchId,
				Collections.singleton(messageId));
		db.commitTransaction(txn);

		// The message should no longer be sendable
		txn = db.startTransaction();
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
	public void testRemoveLostBatch() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact, subscribe to a group and store a message
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		db.addSubscription(txn, groupId);
		db.setSubscriptions(txn, contactId, Collections.singleton(groupId), 1);
		db.addMessage(txn, message);
		db.setSendability(txn, messageId, 1);
		db.setStatus(txn, contactId, messageId, Status.NEW);
		db.commitTransaction(txn);

		// Get the message and mark it as sent
		txn = db.startTransaction();
		Iterator<MessageId> it =
			db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		db.setStatus(txn, contactId, messageId, Status.SENT);
		db.addOutstandingBatch(txn, contactId, batchId,
				Collections.singleton(messageId));
		db.commitTransaction(txn);

		// The message should no longer be sendable
		txn = db.startTransaction();
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
	public void testRetransmission() throws DbException {
		BatchId[] ids = new BatchId[Database.RETRANSMIT_THRESHOLD + 5];
		for(int i = 0; i < ids.length; i++) {
			ids[i] = new BatchId(TestUtils.getRandomId());
		}
		Set<MessageId> empty = Collections.emptySet();
		Database<Connection> db = open(false);

		// Add a contact
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		// Add some outstanding batches, a few ms apart
		for(int i = 0; i < ids.length; i++) {
			db.addOutstandingBatch(txn, contactId, ids[i], empty);
			try {
				Thread.sleep(5);
			} catch(InterruptedException ignored) {}
		}
		// The contact acks the batches in reverse order. The first
		// RETRANSMIT_THRESHOLD - 1 acks should not trigger any retransmissions
		for(int i = 0; i < Database.RETRANSMIT_THRESHOLD - 1; i++) {
			db.removeAckedBatch(txn, contactId, ids[ids.length - i - 1]);
			Set<BatchId> lost = db.getLostBatches(txn, contactId);
			assertEquals(Collections.emptySet(), lost);
		}
		// The next ack should trigger the retransmission of the remaining
		// five outstanding batches
		int index = ids.length - Database.RETRANSMIT_THRESHOLD;
		db.removeAckedBatch(txn, contactId, ids[index]);
		Set<BatchId> lost = db.getLostBatches(txn, contactId);
		for(int i = 0; i < index; i++) {
			assertTrue(lost.contains(ids[i]));
		}
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testNoRetransmission() throws DbException {
		BatchId[] ids = new BatchId[Database.RETRANSMIT_THRESHOLD * 2];
		for(int i = 0; i < ids.length; i++) {
			ids[i] = new BatchId(TestUtils.getRandomId());
		}
		Set<MessageId> empty = Collections.emptySet();
		Database<Connection> db = open(false);

		// Add a contact
		Connection txn = db.startTransaction();
		assertEquals(contactId, db.addContact(txn, null));
		// Add some outstanding batches, a few ms apart
		for(int i = 0; i < ids.length; i++) {
			db.addOutstandingBatch(txn, contactId, ids[i], empty);
			try {
				Thread.sleep(5);
			} catch(InterruptedException ignored) {}
		}
		// The contact acks the batches in the order they were sent - nothing
		// should be retransmitted
		for(int i = 0; i < ids.length; i++) {
			db.removeAckedBatch(txn, contactId, ids[i]);
			Set<BatchId> lost = db.getLostBatches(txn, contactId);
			assertEquals(Collections.emptySet(), lost);
		}
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testGetMessagesByAuthor() throws DbException {
		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, MessageId.NONE, groupId,
				authorId1, timestamp, raw);
		Database<Connection> db = open(false);

		// Subscribe to a group and store two messages
		Connection txn = db.startTransaction();
		db.addSubscription(txn, groupId);
		db.addMessage(txn, message);
		db.addMessage(txn, message1);
		db.commitTransaction(txn);

		// Check that each message is retrievable via its author
		txn = db.startTransaction();
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
	public void testGetNumberOfSendableChildren() throws DbException {
		MessageId childId1 = new MessageId(TestUtils.getRandomId());
		MessageId childId2 = new MessageId(TestUtils.getRandomId());
		MessageId childId3 = new MessageId(TestUtils.getRandomId());
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Message child1 = new TestMessage(childId1, messageId, groupId,
				authorId, timestamp, raw);
		Message child2 = new TestMessage(childId2, messageId, groupId,
				authorId, timestamp, raw);
		// The third child is in a different group
		Message child3 = new TestMessage(childId3, messageId, groupId1,
				authorId, timestamp, raw);
		Database<Connection> db = open(false);

		// Subscribe to the groups and store the messages
		Connection txn = db.startTransaction();
		db.addSubscription(txn, groupId);
		db.addSubscription(txn, groupId1);
		db.addMessage(txn, message);
		db.addMessage(txn, child1);
		db.addMessage(txn, child2);
		db.addMessage(txn, child3);
		// Make all the children sendable
		db.setSendability(txn, childId1, 1);
		db.setSendability(txn, childId2, 5);
		db.setSendability(txn, childId3, 3);
		db.commitTransaction(txn);

		// There should be two sendable children
		txn = db.startTransaction();
		assertEquals(2, db.getNumberOfSendableChildren(txn, messageId));
		// Make one of the children unsendable
		db.setSendability(txn, childId1, 0);
		// Now there should be one sendable child
		assertEquals(1, db.getNumberOfSendableChildren(txn, messageId));
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testGetOldMessages() throws DbException {
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, MessageId.NONE, groupId,
				authorId, timestamp + 1000, raw);
		Database<Connection> db = open(false);

		// Subscribe to a group and store two messages
		Connection txn = db.startTransaction();
		db.addSubscription(txn, groupId);
		db.addMessage(txn, message);
		db.addMessage(txn, message1);
		db.commitTransaction(txn);

		// Allowing enough capacity for one message should return the older one
		txn = db.startTransaction();
		Iterator<MessageId> it = db.getOldMessages(txn, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		db.commitTransaction(txn);

		// Allowing enough capacity for both messages should return both
		txn = db.startTransaction();
		Set<MessageId> ids = new HashSet<MessageId>();
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
		Message message1 = new TestMessage(messageId, MessageId.NONE, groupId,
				authorId, timestamp, largeBody);
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
		db.addSubscription(txn, groupId);
		db.addMessage(txn, message1);
		db.commitTransaction(txn);
		assertTrue(db.getFreeSpace() < free);

		db.close();
	}

	@Test
	public void testCloseWaitsForCommit() throws DbException {
		final AtomicBoolean transactionFinished = new AtomicBoolean(false);
		final AtomicBoolean closed = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread t = new Thread() {
			public void run() {
				try {
					db.close();
					closed.set(true);
					if(!transactionFinished.get()) error.set(true);
				} catch(DbException e) {
					error.set(true);
				}
			}
		};
		t.start();
		// Do whatever the transaction needs to do
		try {
			Thread.sleep(100);
		} catch(InterruptedException ignored) {}
		transactionFinished.set(true);
		// Commit the transaction
		db.commitTransaction(txn);
		// The other thread should now terminate
		try {
			t.join(10 * 1000);
		} catch(InterruptedException ignored) {}
		assertTrue(closed.get());
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testCloseWaitsForAbort() throws DbException {
		final AtomicBoolean transactionFinished = new AtomicBoolean(false);
		final AtomicBoolean closed = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread t = new Thread() {
			public void run() {
				try {
					db.close();
					closed.set(true);
					if(!transactionFinished.get()) error.set(true);
				} catch(DbException e) {
					error.set(true);
				}
			}
		};
		t.start();
		// Do whatever the transaction needs to do
		try {
			Thread.sleep(100);
		} catch(InterruptedException ignored) {}
		transactionFinished.set(true);
		// Abort the transaction
		db.abortTransaction(txn);
		// The other thread should now terminate
		try {
			t.join(10000);
		} catch(InterruptedException ignored) {}
		assertTrue(closed.get());
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testUpdateTransports() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact with some transport details
		Connection txn = db.startTransaction();
		Map<String, String> transports = Collections.singletonMap("foo", "bar");
		assertEquals(contactId, db.addContact(txn, transports));
		assertEquals(transports, db.getTransports(txn, contactId));
		// Replace the transport details
		transports = new TreeMap<String, String>();
		transports.put("foo", "bar baz");
		transports.put("bar", "baz quux");
		db.setTransports(txn, contactId, transports, 1);
		assertEquals(transports, db.getTransports(txn, contactId));
		// Remove the transport details
		db.setTransports(txn, contactId, null, 2);
		assertEquals(Collections.emptyMap(), db.getTransports(txn, contactId));
		// Set the local transport details
		db.setTransports(txn, transports);
		assertEquals(transports, db.getTransports(txn));
		// Remove the local transport details
		db.setTransports(txn, null);
		assertEquals(Collections.emptyMap(), db.getTransports(txn));
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testTransportsNotUpdatedIfTimestampIsOld() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact with some transport details
		Connection txn = db.startTransaction();
		Map<String, String> transports = Collections.singletonMap("foo", "bar");
		assertEquals(contactId, db.addContact(txn, transports));
		assertEquals(transports, db.getTransports(txn, contactId));
		// Replace the transport details using a timestamp of 2
		Map<String, String> transports1 = new TreeMap<String, String>();
		transports1.put("foo", "bar baz");
		transports1.put("bar", "baz quux");
		db.setTransports(txn, contactId, transports1, 2);
		assertEquals(transports1, db.getTransports(txn, contactId));
		// Try to replace the transport details using a timestamp of 1
		Map<String, String> transports2 = new TreeMap<String, String>();
		transports2.put("bar", "baz");
		transports2.put("quux", "fnord");
		db.setTransports(txn, contactId, transports2, 1);
		// The old transports should still be there
		assertEquals(transports1, db.getTransports(txn, contactId));
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testUpdateSubscriptions() throws DbException {
		Database<Connection> db = open(false);

		// Add a contact
		Connection txn = db.startTransaction();
		Map<String, String> transports = Collections.emptyMap();
		assertEquals(contactId, db.addContact(txn, transports));
		// Add some subscriptions
		Set<GroupId> subs = new HashSet<GroupId>();
		subs.add(new GroupId(TestUtils.getRandomId()));
		subs.add(new GroupId(TestUtils.getRandomId()));
		db.setSubscriptions(txn, contactId, subs, 1);
		assertEquals(subs, db.getSubscriptions(txn, contactId));
		// Update the subscriptions
		Set<GroupId> subs1 = new HashSet<GroupId>();
		subs1.add(new GroupId(TestUtils.getRandomId()));
		subs1.add(new GroupId(TestUtils.getRandomId()));
		db.setSubscriptions(txn, contactId, subs1, 2);
		assertEquals(subs1, db.getSubscriptions(txn, contactId));
		db.commitTransaction(txn);

		db.close();
	}

	@Test
	public void testSubscriptionsNotUpdatedIfTimestampIsOld()
	throws DbException {
		Database<Connection> db = open(false);

		// Add a contact
		Connection txn = db.startTransaction();
		Map<String, String> transports = Collections.emptyMap();
		assertEquals(contactId, db.addContact(txn, transports));
		// Add some subscriptions
		Set<GroupId> subs = new HashSet<GroupId>();
		subs.add(new GroupId(TestUtils.getRandomId()));
		subs.add(new GroupId(TestUtils.getRandomId()));
		db.setSubscriptions(txn, contactId, subs, 2);
		assertEquals(subs, db.getSubscriptions(txn, contactId));
		// Try to update the subscriptions using a timestamp of 1
		Set<GroupId> subs1 = new HashSet<GroupId>();
		subs1.add(new GroupId(TestUtils.getRandomId()));
		subs1.add(new GroupId(TestUtils.getRandomId()));
		db.setSubscriptions(txn, contactId, subs1, 1);
		// The old subscriptions should still be there
		assertEquals(subs, db.getSubscriptions(txn, contactId));
		db.commitTransaction(txn);

		db.close();
	}

	private Database<Connection> open(boolean resume) throws DbException {
		final char[] passwordArray = passwordString.toCharArray();
		Mockery context = new Mockery();
		final Password password = context.mock(Password.class);
		context.checking(new Expectations() {{
			oneOf(password).getPassword();
			will(returnValue(passwordArray));
		}});
		Database<Connection> db = new H2Database(testDir, password, MAX_SIZE);
		db.open(resume);
		context.assertIsSatisfied();
		// The password array should be cleared after use
		assertTrue(Arrays.equals(new char[passwordString.length()],
				passwordArray));
		return db;
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
