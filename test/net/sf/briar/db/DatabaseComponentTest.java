package net.sf.briar.db;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public abstract class DatabaseComponentTest extends TestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;

	protected final Object txn = new Object();
	protected final AuthorId authorId;
	protected final BatchId batchId;
	protected final ContactId contactId;
	protected final GroupId groupId;
	protected final MessageId messageId, parentId;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message;
	private final Group group;
	private final Map<String, String> transports;

	public DatabaseComponentTest() {
		super();
		authorId = new AuthorId(TestUtils.getRandomId());
		batchId = new BatchId(TestUtils.getRandomId());
		contactId = new ContactId(123);
		groupId = new GroupId(TestUtils.getRandomId());
		messageId = new MessageId(TestUtils.getRandomId());
		parentId = new MessageId(TestUtils.getRandomId());
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		message = new TestMessage(messageId, MessageId.NONE, groupId, authorId,
				timestamp, raw);
		group = new TestGroup(groupId, "The really exciting group", null);
		transports = Collections.singletonMap("foo", "bar");
	}

	protected abstract <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner);

	@Test
	public void testSimpleCalls() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final Group group = context.mock(Group.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			// open(false)
			oneOf(database).open(false);
			oneOf(cleaner).startCleaning();
			// getRating(authorId)
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			// addContact(transports)
			oneOf(database).addContact(txn, transports);
			will(returnValue(contactId));
			// getContacts()
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			// getTransports(contactId)
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransports(txn, contactId);
			will(returnValue(transports));
			// subscribe(group)
			oneOf(database).addSubscription(txn, group);
			// getSubscriptions()
			oneOf(database).getSubscriptions(txn);
			will(returnValue(Collections.singletonList(groupId)));
			// unsubscribe(groupId)
			oneOf(database).removeSubscription(txn, groupId);
			// removeContact(contactId)
			oneOf(database).removeContact(txn, contactId);
			// close()
			oneOf(cleaner).stopCleaning();
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.open(false);
		assertEquals(Rating.UNRATED, db.getRating(authorId));
		assertEquals(contactId, db.addContact(transports));
		assertEquals(Collections.singletonList(contactId), db.getContacts());
		assertEquals(transports, db.getTransports(contactId));
		db.subscribe(group);
		assertEquals(Collections.singletonList(groupId), db.getSubscriptions());
		db.unsubscribe(groupId);
		db.removeContact(contactId);
		db.close();

		context.assertIsSatisfied();
	}

	@Test
	public void testNoParentStopsBackwardInclusion() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			allowing(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// Backward inclusion stops when the message has no parent
			oneOf(database).getParent(txn, messageId);
			will(returnValue(MessageId.NONE));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testMissingParentStopsBackwardInclusion() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			// The parent isn't in the DB
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testChangingGroupsStopsBackwardInclusion() throws DbException {
		final GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists and is in the database
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			// The parent is in a different group
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(groupId1));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testUnaffectedParentStopsBackwardInclusion()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists, is in the DB, and is in the same group
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(groupId));
			// The parent is already sendable
			oneOf(database).getSendability(txn, parentId);
			will(returnValue(1));
			oneOf(database).setSendability(txn, parentId, 2);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testAffectedParentContinuesBackwardInclusion()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists, is in the DB, and is in the same group
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(groupId));
			// The parent is not already sendable
			oneOf(database).getSendability(txn, parentId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, parentId, 1);
			oneOf(database).getParent(txn, parentId);
			will(returnValue(MessageId.NONE));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testMessagesAreNotStoredUnlessSubscribed()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicateMessagesAreNotStored() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocallyGeneratedMessage() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(true));
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(database).setStatus(txn, contactId, messageId, Status.NEW);
			// The author is unrated and there are no sendable children
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 0);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingASendableMessageTriggersBackwardInclusion()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(true));
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(database).setStatus(txn, contactId, messageId, Status.NEW);
			// The author is rated GOOD and there are two sendable children
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.GOOD));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(2));
			oneOf(database).setSendability(txn, messageId, 3);
			// The sendability of the message's ancestors should be updated
			oneOf(database).getParent(txn, messageId);
			will(returnValue(MessageId.NONE));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfContactIsMissing()
	throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final AckWriter ackWriter = context.mock(AckWriter.class);
		final BatchWriter batchWriter = context.mock(BatchWriter.class);
		final OfferWriter offerWriter = context.mock(OfferWriter.class);
		final SubscriptionWriter subscriptionWriter =
			context.mock(SubscriptionWriter.class);
		final TransportWriter transportWriter =
			context.mock(TransportWriter.class);
		final Ack ack = context.mock(Ack.class);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			// Check whether the contact is still in the DB - which it's not
			exactly(8).of(database).startTransaction();
			will(returnValue(txn));
			exactly(8).of(database).containsContact(txn, contactId);
			will(returnValue(false));
			exactly(8).of(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		try {
			db.generateAck(contactId, ackWriter);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		try {
			db.generateBatch(contactId, batchWriter);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		try {
			db.generateBatch(contactId, batchWriter,
					Collections.<MessageId>emptyList());
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		try {
			db.generateOffer(contactId, offerWriter);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		try {
			db.generateSubscriptions(contactId, subscriptionWriter);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		try {
			db.generateTransports(contactId, transportWriter);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveAck(contactId, ack);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveBatch(contactId, batch);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateAck() throws Exception {
		final BatchId batchId1 = new BatchId(TestUtils.getRandomId());
		final Collection<BatchId> batchesToAck = new ArrayList<BatchId>();
		batchesToAck.add(batchId);
		batchesToAck.add(batchId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final AckWriter ackWriter = context.mock(AckWriter.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the batches to ack
			oneOf(database).getBatchesToAck(txn, contactId);
			will(returnValue(batchesToAck));
			// Try to add both batches to the writer - only manage to add one
			oneOf(ackWriter).writeBatchId(batchId);
			will(returnValue(true));
			oneOf(ackWriter).writeBatchId(batchId1);
			will(returnValue(false));
			oneOf(ackWriter).finish();
			// Record the batch that was acked
			oneOf(database).removeBatchesToAck(txn, contactId,
					Collections.singletonList(batchId));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.generateAck(contactId, ackWriter);

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateBatch() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final byte[] raw1 = new byte[size];
		final Collection<MessageId> sendable = new ArrayList<MessageId>();
		sendable.add(messageId);
		sendable.add(messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final BatchWriter batchWriter = context.mock(BatchWriter.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the sendable messages
			oneOf(batchWriter).getCapacity();
			will(returnValue(ONE_MEGABYTE));
			oneOf(database).getSendableMessages(txn, contactId, ONE_MEGABYTE);
			will(returnValue(sendable));
			// Try to add both messages to the writer - only manage to add one
			oneOf(database).getMessage(txn, messageId);
			will(returnValue(raw));
			oneOf(batchWriter).writeMessage(raw);
			will(returnValue(true));
			oneOf(database).getMessage(txn, messageId1);
			will(returnValue(raw1));
			oneOf(batchWriter).writeMessage(raw1);
			will(returnValue(false));
			oneOf(batchWriter).finish();
			will(returnValue(batchId));
			// Record the message that was sent
			oneOf(database).addOutstandingBatch(txn, contactId, batchId,
					Collections.singletonList(messageId));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.generateBatch(contactId, batchWriter);

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateBatchFromRequest() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		final byte[] raw1 = new byte[size];
		final Collection<MessageId> requested = new ArrayList<MessageId>();
		requested.add(messageId);
		requested.add(messageId1);
		requested.add(messageId2);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final BatchWriter batchWriter = context.mock(BatchWriter.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Try to get the requested messages and add them to the writer
			oneOf(database).getMessageIfSendable(txn, contactId, messageId);
			will(returnValue(raw)); // Message is sendable
			oneOf(batchWriter).writeMessage(raw);
			will(returnValue(true)); // Message added to batch
			oneOf(database).getMessageIfSendable(txn, contactId, messageId1);
			will(returnValue(null)); // Message is not sendable
			oneOf(database).getMessageIfSendable(txn, contactId, messageId2);
			will(returnValue(raw1)); // Message is sendable
			oneOf(batchWriter).writeMessage(raw1);
			will(returnValue(false)); // Message not added to batch
			oneOf(batchWriter).finish();
			will(returnValue(batchId));
			// Record the message that was sent
			oneOf(database).addOutstandingBatch(txn, contactId, batchId,
					Collections.singletonList(messageId));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.generateBatch(contactId, batchWriter, requested);

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateOffer() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> sendable = new ArrayList<MessageId>();
		sendable.add(messageId);
		sendable.add(messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final OfferWriter offerWriter = context.mock(OfferWriter.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the sendable message IDs
			oneOf(database).getSendableMessages(txn, contactId,
					Integer.MAX_VALUE);
			will(returnValue(sendable));
			// Try to add both IDs to the writer - only manage to add one
			oneOf(offerWriter).writeMessageId(messageId);
			will(returnValue(true));
			oneOf(offerWriter).writeMessageId(messageId1);
			will(returnValue(false));
			oneOf(offerWriter).finish();
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		assertEquals(Collections.singletonList(messageId),
				db.generateOffer(contactId, offerWriter));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateSubscriptions() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> sendable = new ArrayList<MessageId>();
		sendable.add(messageId);
		sendable.add(messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final SubscriptionWriter subscriptionWriter =
			context.mock(SubscriptionWriter.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the local subscriptions
			oneOf(database).getSubscriptions(txn);
			will(returnValue(Collections.singletonList(group)));
			// Add the subscriptions to the writer
			oneOf(subscriptionWriter).writeSubscriptions(
					Collections.singletonList(group));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.generateSubscriptions(contactId, subscriptionWriter);

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateTransports() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> sendable = new ArrayList<MessageId>();
		sendable.add(messageId);
		sendable.add(messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final TransportWriter transportWriter =
			context.mock(TransportWriter.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the local transport details
			oneOf(database).getTransports(txn);
			will(returnValue(transports));
			// Add the transports to the writer
			oneOf(transportWriter).writeTransports(transports);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.generateTransports(contactId, transportWriter);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveAck() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final Ack ack = context.mock(Ack.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the acked batches
			oneOf(ack).getBatches();
			will(returnValue(Collections.singletonList(batchId)));
			oneOf(database).removeAckedBatch(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveAck(contactId, ack);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchDoesNotStoreIfNotSubscribed() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Only store messages belonging to subscribed groups
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			// The message is not stored but the batch must still be acked
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addBatchToAck(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchDoesNotCalculateSendabilityForDuplicates()
	throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Only store messages belonging to subscribed groups
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			// The message is stored, but it's a duplicate
			oneOf(database).addMessage(txn, message);
			will(returnValue(false));
			oneOf(database).setStatus(txn, contactId, messageId, Status.SEEN);
			// The batch needs to be acknowledged
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addBatchToAck(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchCalculatesSendability() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Only store messages belonging to subscribed groups
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			// The message is stored, and it's not a duplicate
			oneOf(database).addMessage(txn, message);
			will(returnValue(true));
			oneOf(database).setStatus(txn, contactId, messageId, Status.SEEN);
			// Set the status to NEW for all other contacts (there are none)
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			// Calculate the sendability - zero, so ancestors aren't updated
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 0);
			// The batch needs to be acknowledged
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addBatchToAck(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchUpdatesAncestorSendability() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Only store messages belonging to subscribed groups
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			// The message is stored, and it's not a duplicate
			oneOf(database).addMessage(txn, message);
			will(returnValue(true));
			oneOf(database).setStatus(txn, contactId, messageId, Status.SEEN);
			// Set the status to NEW for all other contacts (there are none)
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			// Calculate the sendability -ancestors are updated
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.GOOD));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(1));
			oneOf(database).setSendability(txn, messageId, 2);
			oneOf(database).getParent(txn, messageId);
			will(returnValue(MessageId.NONE));
			// The batch needs to be acknowledged
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addBatchToAck(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveOffer() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> offered = new ArrayList<MessageId>();
		offered.add(messageId);
		offered.add(messageId1);
		offered.add(messageId2);
		final BitSet expectedRequest = new BitSet(3);
		expectedRequest.set(0);
		expectedRequest.set(2);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final Offer offer = context.mock(Offer.class);
		final RequestWriter requestWriter = context.mock(RequestWriter.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the offered messages
			oneOf(offer).getMessages();
			will(returnValue(offered));
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId);
			will(returnValue(false)); // Not visible - request # 0
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId1);
			will(returnValue(true)); // Visible - do not request # 1
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId2);
			will(returnValue(false)); // Not visible - request # 2
			oneOf(requestWriter).writeBitmap(expectedRequest);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveOffer(contactId, offer, requestWriter);

		context.assertIsSatisfied();
	}
}
