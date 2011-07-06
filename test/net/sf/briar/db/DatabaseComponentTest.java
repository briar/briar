package net.sf.briar.db;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Bundle;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.protocol.MessageImpl;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Provider;

public abstract class DatabaseComponentTest extends TestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;

	protected final Object txn = new Object();
	protected final AuthorId authorId;
	protected final BatchId batchId;
	protected final BundleId bundleId;
	protected final ContactId contactId;
	protected final GroupId groupId;
	protected final MessageId messageId, parentId;
	private final long timestamp;
	private final int size;
	private final byte[] body;
	private final Message message;

	public DatabaseComponentTest() {
		super();
		authorId = new AuthorId(TestUtils.getRandomId());
		batchId = new BatchId(TestUtils.getRandomId());
		bundleId = new BundleId(TestUtils.getRandomId());
		contactId = new ContactId(123);
		groupId = new GroupId(TestUtils.getRandomId());
		messageId = new MessageId(TestUtils.getRandomId());
		parentId = new MessageId(TestUtils.getRandomId());
		timestamp = System.currentTimeMillis();
		size = 1234;
		body = new byte[size];
		message = new MessageImpl(messageId, MessageId.NONE, groupId, authorId,
				timestamp, body);
	}

	protected abstract <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner,
			Provider<Batch> batchProvider);

	@Test
	public void testSimpleCalls() throws DbException {
		final Map<String, String> transports =
			Collections.singletonMap("foo", "bar");
		final Map<String, String> transports1 =
			Collections.singletonMap("foo", "bar baz");
		final Set<GroupId> subs = Collections.singleton(groupId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
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
			will(returnValue(Collections.singleton(contactId)));
			// getTransports(contactId)
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransports(txn, contactId);
			will(returnValue(transports));
			// setTransports(contactId, transports1)
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setTransports(txn, contactId, transports1);
			// subscribe(groupId)
			oneOf(database).addSubscription(txn, groupId);
			// getSubscriptions()
			oneOf(database).getSubscriptions(txn);
			will(returnValue(subs));
			// unsubscribe(groupId)
			oneOf(database).removeSubscription(txn, groupId);
			// removeContact(contactId)
			oneOf(database).removeContact(txn, contactId);
			// close()
			oneOf(cleaner).stopCleaning();
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.open(false);
		assertEquals(Rating.UNRATED, db.getRating(authorId));
		assertEquals(contactId, db.addContact(transports));
		assertEquals(Collections.singleton(contactId), db.getContacts());
		assertEquals(transports, db.getTransports(contactId));
		db.setTransports(contactId, transports1);
		db.subscribe(groupId);
		assertEquals(Collections.singleton(groupId), db.getSubscriptions());
		db.unsubscribe(groupId);
		db.removeContact(contactId);
		db.close();

		context.assertIsSatisfied();
	}

	@Test
	public void testNoParentStopsBackwardInclusion() throws DbException {
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			allowing(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// Backward inclusion stops when the message has no parent
			oneOf(database).getParent(txn, messageId);
			will(returnValue(MessageId.NONE));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testMissingParentStopsBackwardInclusion() throws DbException {
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testChangingGroupsStopsBackwardInclusion() throws DbException {
		final GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testUnaffectedParentStopsBackwardInclusion()
	throws DbException {
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testAffectedParentContinuesBackwardInclusion()
	throws DbException {
		final Set<MessageId> messages = Collections.singleton(messageId);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// setRating(Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(messages));
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

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
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicateMessagesAreNotStored() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocallyGeneratedMessage() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(true));
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singleton(contactId)));
			oneOf(database).setStatus(txn, contactId, messageId, Status.NEW);
			// The author is unrated and there are no sendable children
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 0);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

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
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		context.checking(new Expectations() {{
			// addLocallyGeneratedMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(true));
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singleton(contactId)));
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.addLocallyGeneratedMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateBundleThrowsExceptionIfContactIsMissing()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		final Bundle bundle = context.mock(Bundle.class);
		context.checking(new Expectations() {{
			// Check that the contact is still in the DB
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		try {
			db.generateBundle(contactId, bundle);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateBundle() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		final Bundle bundle = context.mock(Bundle.class);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Add acks to the bundle
			oneOf(database).removeBatchesToAck(txn, contactId);
			will(returnValue(Collections.singleton(batchId)));
			oneOf(bundle).addAck(batchId);
			// Add subscriptions to the bundle
			oneOf(database).getSubscriptions(txn);
			will(returnValue(Collections.singleton(groupId)));
			oneOf(bundle).addSubscription(groupId);
			// Add transports to the bundle
			oneOf(database).getTransports(txn);
			will(returnValue(Collections.singletonMap("foo", "bar")));
			oneOf(bundle).addTransport("foo", "bar");
			// Prepare to add batches to the bundle
			oneOf(bundle).getCapacity();
			will(returnValue((long) ONE_MEGABYTE));
			// Add messages to the batch
			oneOf(database).getSendableMessages(txn, contactId, ONE_MEGABYTE);
			will(returnValue(Collections.singleton(messageId)));
			oneOf(batchProvider).get();
			will(returnValue(batch));
			oneOf(database).getMessage(txn, messageId);
			will(returnValue(message));
			oneOf(batch).addMessage(message);
			oneOf(batch).seal();
			// Record the batch as outstanding
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addOutstandingBatch(txn, contactId, batchId,
					Collections.singleton(messageId));
			// Add the batch to the bundle
			oneOf(bundle).addBatch(batch);
			// Check whether to add another batch
			oneOf(batch).getSize();
			will(returnValue((long) message.getSize()));
			// Nope
			oneOf(bundle).seal();
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.generateBundle(contactId, bundle);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBundleThrowsExceptionIfContactIsMissing()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		final Bundle bundle = context.mock(Bundle.class);
		context.checking(new Expectations() {{
			// Check that the contact is still in the DB
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		try {
			db.receiveBundle(contactId, bundle);
			assertTrue(false);
		} catch(NoSuchContactException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testReceivedBundle() throws DbException {
		final Map<String, String> transports =
			Collections.singletonMap("foo", "bar");
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		@SuppressWarnings("unchecked")
		final Provider<Batch> batchProvider = context.mock(Provider.class);
		final Bundle bundle = context.mock(Bundle.class);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Acks
			oneOf(bundle).getAcks();
			will(returnValue(Collections.singleton(batchId)));
			oneOf(database).removeAckedBatch(txn, contactId, batchId);
			// Subscriptions
			oneOf(database).clearSubscriptions(txn, contactId);
			oneOf(bundle).getSubscriptions();
			will(returnValue(Collections.singleton(groupId)));
			oneOf(database).addSubscription(txn, contactId, groupId);
			// Transports
			oneOf(bundle).getTransports();
			will(returnValue(transports));
			oneOf(database).setTransports(txn, contactId, transports);
			// Batches
			oneOf(bundle).getBatches();
			will(returnValue(Collections.singleton(batch)));
			oneOf(batch).getMessages();
			will(returnValue(Collections.singleton(message)));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message);
			will(returnValue(false)); // Duplicate message
			oneOf(database).setStatus(txn, contactId, messageId, Status.SEEN);
			// Batch to ack
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addBatchToAck(txn, contactId, batchId);
			// Lost batches
			oneOf(bundle).getId();
			will(returnValue(bundleId));
			oneOf(database).addReceivedBundle(txn, contactId, bundleId);
			will(returnValue(Collections.singleton(batchId)));
			oneOf(database).removeLostBatch(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				batchProvider);

		db.receiveBundle(contactId, bundle);

		context.assertIsSatisfied();
	}
}
