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
import net.sf.briar.api.db.DatabaseListener;
import net.sf.briar.api.db.DatabaseListener.Event;
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
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.transport.ConnectionWindow;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public abstract class DatabaseComponentTest extends TestCase {

	protected final Object txn = new Object();
	protected final AuthorId authorId;
	protected final BatchId batchId;
	protected final ContactId contactId;
	protected final GroupId groupId;
	protected final MessageId messageId, parentId;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message, privateMessage;
	private final Group group;
	private final Map<String, Map<String, String>> transports;
	private final byte[] secret;

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
		message =
			new TestMessage(messageId, null, groupId, authorId, timestamp, raw);
		privateMessage =
			new TestMessage(messageId, null, null, null, timestamp, raw);
		group = new TestGroup(groupId, "The really exciting group", null);
		transports = Collections.singletonMap("foo",
				Collections.singletonMap("bar", "baz"));
		secret = new byte[123];
	}

	protected abstract <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner);

	@Test
	public void testSimpleCalls() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ConnectionWindow connectionWindow =
			context.mock(ConnectionWindow.class);
		final Group group = context.mock(Group.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			// open(false)
			oneOf(database).open(false);
			oneOf(cleaner).startCleaning(
					with(any(DatabaseCleaner.Callback.class)),
					with(any(long.class)));
			// getRating(authorId)
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			// addContact(transports)
			oneOf(database).addContact(txn, transports, secret);
			will(returnValue(contactId));
			oneOf(listener).eventOccurred(Event.CONTACTS_UPDATED);
			// getContacts()
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			// getConnectionWindow(contactId, 123)
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getConnectionWindow(txn, contactId, 123);
			will(returnValue(connectionWindow));
			// getSharedSecret(contactId)
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getSharedSecret(txn, contactId);
			will(returnValue(secret));
			// getTransports(contactId)
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransports(txn, contactId);
			will(returnValue(transports));
			// subscribe(group)
			oneOf(group).getId();
			will(returnValue(groupId));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			oneOf(database).addSubscription(txn, group);
			oneOf(listener).eventOccurred(Event.SUBSCRIPTIONS_UPDATED);
			// subscribe(group) again
			oneOf(group).getId();
			will(returnValue(groupId));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			// getSubscriptions()
			oneOf(database).getSubscriptions(txn);
			will(returnValue(Collections.singletonList(groupId)));
			// unsubscribe(groupId)
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).removeSubscription(txn, groupId);
			oneOf(listener).eventOccurred(Event.SUBSCRIPTIONS_UPDATED);
			// unsubscribe(groupId) again
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			// setConnectionWindow(contactId, 123, connectionWindow)
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setConnectionWindow(txn, contactId, 123,
					connectionWindow);
			// removeContact(contactId)
			oneOf(database).removeContact(txn, contactId);
			oneOf(listener).eventOccurred(Event.CONTACTS_UPDATED);
			// close()
			oneOf(cleaner).stopCleaning();
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.open(false);
		db.addListener(listener);
		assertEquals(Rating.UNRATED, db.getRating(authorId));
		assertEquals(contactId, db.addContact(transports, secret));
		assertEquals(Collections.singletonList(contactId), db.getContacts());
		assertEquals(connectionWindow, db.getConnectionWindow(contactId, 123));
		assertEquals(secret, db.getSharedSecret(contactId));
		assertEquals(transports, db.getTransports(contactId));
		db.subscribe(group); // First time - check listeners are called
		db.subscribe(group); // Second time - check listeners aren't called
		assertEquals(Collections.singletonList(groupId), db.getSubscriptions());
		db.unsubscribe(groupId); // First time - check listeners are called
		db.unsubscribe(groupId); // Second time - check listeners aren't called
		db.setConnectionWindow(contactId, 123, connectionWindow);
		db.removeContact(contactId);
		db.removeListener(listener);
		db.close();

		context.assertIsSatisfied();
	}

	@Test
	public void testNullParentStopsBackwardInclusion() throws DbException {
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
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getParent(txn, messageId);
			will(returnValue(null));
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
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
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
	public void testParentInAnotherGroupStopsBackwardInclusion()
	throws DbException {
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
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			// The parent is in a different group
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(groupId1));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testPrivateParentStopsBackwardInclusion() throws DbException {
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
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			// The parent is a private message
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(null));
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
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
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
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getParent(txn, messageId);
			will(returnValue(parentId));
			oneOf(database).containsMessage(txn, parentId);
			will(returnValue(true));
			oneOf(database).getGroup(txn, parentId);
			will(returnValue(groupId));
			// The parent is not already sendable
			oneOf(database).getSendability(txn, parentId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, parentId, 1);
			oneOf(database).getParent(txn, parentId);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testGroupMessagesAreNotStoredUnlessSubscribed()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId, timestamp);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicateGroupMessagesAreNotStored() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId, timestamp);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocalGroupMessage() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId, timestamp);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
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

		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingSendableMessageTriggersBackwardInclusion()
	throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId, timestamp);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
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
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getParent(txn, messageId);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicatePrivateMessagesAreNotStored() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// addLocalPrivateMessage(privateMessage, contactId)
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(false));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocalPrivateMessage(privateMessage, contactId);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocalPrivateMessage() throws DbException {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// addLocalPrivateMessage(privateMessage, contactId)
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(true));
			oneOf(database).setStatus(txn, contactId, messageId, Status.NEW);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addLocalPrivateMessage(privateMessage, contactId);

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
		final Offer offer = context.mock(Offer.class);
		final RequestWriter requestWriter = context.mock(RequestWriter.class);
		final SubscriptionUpdate subscriptionsUpdate =
			context.mock(SubscriptionUpdate.class);
		final TransportUpdate transportsUpdate = context.mock(TransportUpdate.class);
		context.checking(new Expectations() {{
			// Check whether the contact is still in the DB (which it's not)
			exactly(18).of(database).startTransaction();
			will(returnValue(txn));
			exactly(18).of(database).containsContact(txn, contactId);
			will(returnValue(false));
			exactly(18).of(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		try {
			db.addLocalPrivateMessage(privateMessage, contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateAck(contactId, ackWriter);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateBatch(contactId, batchWriter);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateBatch(contactId, batchWriter,
					Collections.<MessageId>emptyList());
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateOffer(contactId, offerWriter);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateSubscriptionUpdate(contactId, subscriptionWriter);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateTransportUpdate(contactId, transportWriter);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.getConnectionWindow(contactId, 123);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.getSharedSecret(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.getTransports(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.hasSendableMessages(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveAck(contactId, ack);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveBatch(contactId, batch);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveOffer(contactId, offer, requestWriter);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveSubscriptionUpdate(contactId, subscriptionsUpdate);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveTransportUpdate(contactId, transportsUpdate);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.setConnectionWindow(contactId, 123, null);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.setSeen(contactId, Collections.singleton(messageId));
			fail();
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
			// Find out how much space we've got
			oneOf(batchWriter).getCapacity();
			will(returnValue(ProtocolConstants.MAX_PACKET_LENGTH));
			// Get the sendable messages
			oneOf(database).getSendableMessages(txn, contactId,
					ProtocolConstants.MAX_PACKET_LENGTH);
			will(returnValue(sendable));
			oneOf(database).getMessage(txn, messageId);
			will(returnValue(raw));
			oneOf(database).getMessage(txn, messageId1);
			will(returnValue(raw1));
			// Add the sendable messages to the batch
			oneOf(batchWriter).writeMessage(raw);
			will(returnValue(true));
			oneOf(batchWriter).writeMessage(raw1);
			will(returnValue(true));
			oneOf(batchWriter).finish();
			will(returnValue(batchId));
			// Record the message that was sent
			oneOf(database).addOutstandingBatch(txn, contactId, batchId,
					sendable);
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
			// Find out how much space we've got
			oneOf(batchWriter).getCapacity();
			will(returnValue(ProtocolConstants.MAX_PACKET_LENGTH));
			// Try to get the requested messages
			oneOf(database).getMessageIfSendable(txn, contactId, messageId);
			will(returnValue(null)); // Message is not sendable
			oneOf(database).getMessageIfSendable(txn, contactId, messageId1);
			will(returnValue(raw1)); // Message is sendable
			oneOf(database).getMessageIfSendable(txn, contactId, messageId2);
			will(returnValue(null)); // Message is not sendable
			// Add the sendable message to the batch
			oneOf(batchWriter).writeMessage(raw1);
			will(returnValue(true));
			oneOf(batchWriter).finish();
			will(returnValue(batchId));
			// Record the message that was sent
			oneOf(database).addOutstandingBatch(txn, contactId, batchId,
					Collections.singletonList(messageId1));
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
			oneOf(database).getSendableMessages(txn, contactId);
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
	public void testGenerateSubscriptionUpdate() throws Exception {
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
			// Get the visible subscriptions
			oneOf(database).getVisibleSubscriptions(txn, contactId);
			will(returnValue(Collections.singletonMap(group, 0L)));
			// Add the subscriptions to the writer
			oneOf(subscriptionWriter).writeSubscriptions(
					with(Collections.singletonMap(group, 0L)),
					with(any(long.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.generateSubscriptionUpdate(contactId, subscriptionWriter);

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateTransportUpdate() throws Exception {
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
			// Get the local transport properties
			oneOf(database).getTransports(txn);
			will(returnValue(transports));
			// Add the properties to the writer
			oneOf(transportWriter).writeTransports(with(transports),
					with(any(long.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.generateTransportUpdate(contactId, transportWriter);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveAck() throws Exception {
		final BatchId batchId1 = new BatchId(TestUtils.getRandomId());
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
			oneOf(ack).getBatchIds();
			will(returnValue(Collections.singletonList(batchId)));
			oneOf(database).removeAckedBatch(txn, contactId, batchId);
			// Find lost batches
			oneOf(database).getLostBatches(txn, contactId);
			will(returnValue(Collections.singletonList(batchId1)));
			oneOf(database).removeLostBatch(txn, contactId, batchId1);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveAck(contactId, ack);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchStoresPrivateMessage() throws Exception {
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
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(privateMessage)));
			// The message is stored
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(true));
			oneOf(database).setStatus(txn, contactId, messageId, Status.SEEN);
			// The batch must be acked
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addBatchToAck(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchWithDuplicatePrivateMessage() throws Exception {
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
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(privateMessage)));
			// The message is stored, but it's a duplicate
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(false));
			// The batch must still be acked
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addBatchToAck(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchDoesNotStoreGroupMessageUnlessSubscribed()
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
			// Only store messages belonging to visible, subscribed groups
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			oneOf(database).containsVisibleSubscription(txn, groupId,
					contactId, timestamp);
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
			// Only store messages belonging to visible, subscribed groups
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			oneOf(database).containsVisibleSubscription(txn, groupId,
					contactId, timestamp);
			will(returnValue(true));
			// The message is stored, but it's a duplicate
			oneOf(database).addGroupMessage(txn, message);
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
			// Only store messages belonging to visible, subscribed groups
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			oneOf(database).containsVisibleSubscription(txn, groupId,
					contactId, timestamp);
			will(returnValue(true));
			// The message is stored, and it's not a duplicate
			oneOf(database).addGroupMessage(txn, message);
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
			// Only store messages belonging to visible, subscribed groups
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			oneOf(database).containsVisibleSubscription(txn, groupId,
					contactId, timestamp);
			will(returnValue(true));
			// The message is stored, and it's not a duplicate
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(true));
			oneOf(database).setStatus(txn, contactId, messageId, Status.SEEN);
			// Set the status to NEW for all other contacts (there are none)
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			// Calculate the sendability - ancestors are updated
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.GOOD));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(1));
			oneOf(database).setSendability(txn, messageId, 2);
			oneOf(database).getGroup(txn, messageId);
			will(returnValue(groupId));
			oneOf(database).getParent(txn, messageId);
			will(returnValue(null));
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
			oneOf(offer).getMessageIds();
			will(returnValue(offered));
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId);
			will(returnValue(false)); // Not visible - request message # 0
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId1);
			will(returnValue(true)); // Visible - do not request message # 1
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId2);
			will(returnValue(false)); // Not visible - request message # 2
			oneOf(requestWriter).writeRequest(expectedRequest, 3);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveOffer(contactId, offer, requestWriter);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveSubscriptionUpdate() throws Exception {
		final long timestamp = 1234L;
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final SubscriptionUpdate subscriptionUpdate =
			context.mock(SubscriptionUpdate.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the contents of the update
			oneOf(subscriptionUpdate).getSubscriptions();
			will(returnValue(Collections.singletonMap(group, 0L)));
			oneOf(subscriptionUpdate).getTimestamp();
			will(returnValue(timestamp));
			oneOf(database).setSubscriptions(txn, contactId,
					Collections.singletonMap(group, 0L), timestamp);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveSubscriptionUpdate(contactId, subscriptionUpdate);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveTransportUpdate() throws Exception {
		final long timestamp = 1234L;
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final TransportUpdate transportUpdate =
			context.mock(TransportUpdate.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the contents of the update
			oneOf(transportUpdate).getTransports();
			will(returnValue(transports));
			oneOf(transportUpdate).getTimestamp();
			will(returnValue(timestamp));
			oneOf(database).setTransports(txn, contactId, transports,
					timestamp);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.receiveTransportUpdate(contactId, transportUpdate);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingGroupMessageCallsListeners() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId, timestamp);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(true));
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(database).setStatus(txn, contactId, messageId, Status.NEW);
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 0);
			oneOf(database).commitTransaction(txn);
			// The message was added, so the listener should be called
			oneOf(listener).eventOccurred(Event.MESSAGES_ADDED);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addListener(listener);
		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingPrivateMessageCallsListeners() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// addLocalPrivateMessage(privateMessage, contactId)
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(true));
			oneOf(database).setStatus(txn, contactId, messageId, Status.NEW);
			// The message was added, so the listener should be called
			oneOf(listener).eventOccurred(Event.MESSAGES_ADDED);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addListener(listener);
		db.addLocalPrivateMessage(privateMessage, contactId);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingDuplicateGroupMessageDoesNotCallListeners()
	throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId, timestamp);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
			// The message was not added, so the listener should not be called
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addListener(listener);
		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingDuplicatePrivateMessageDoesNotCallListeners()
	throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// addLocalPrivateMessage(privateMessage, contactId)
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(false));
			// The message was not added, so the listener should not be called
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addListener(listener);
		db.addLocalPrivateMessage(privateMessage, contactId);

		context.assertIsSatisfied();
	}

	@Test
	public void testTransportPropertiesChangedCallsListeners()
	throws Exception {
		final Map<String, String> properties =
			Collections.singletonMap("bar", "baz");
		final Map<String, String> properties1 =
			Collections.singletonMap("baz", "bam");
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getTransports(txn);
			will(returnValue(Collections.singletonMap("foo", properties)));
			oneOf(database).setTransportProperties(txn, "foo", properties1);
			oneOf(database).commitTransaction(txn);
			oneOf(listener).eventOccurred(Event.TRANSPORTS_UPDATED);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addListener(listener);
		db.setTransportProperties("foo", properties1);

		context.assertIsSatisfied();
	}

	@Test
	public void testTransportPropertiesUnchangedDoesNotCallListeners()
	throws Exception {
		final Map<String, String> properties =
			Collections.singletonMap("bar", "baz");
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getTransports(txn);
			will(returnValue(Collections.singletonMap("foo", properties)));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addListener(listener);
		db.setTransportProperties("foo", properties);

		context.assertIsSatisfied();
	}

	@Test
	public void testTransportConfigChangedCallsListeners() throws Exception {
		final Map<String, String> config =
			Collections.singletonMap("bar", "baz");
		final Map<String, String> config1 =
			Collections.singletonMap("baz", "bam");
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getTransportConfig(txn, "foo");
			will(returnValue(config));
			oneOf(database).setTransportConfig(txn, "foo", config1);
			oneOf(database).commitTransaction(txn);
			oneOf(listener).eventOccurred(Event.TRANSPORTS_UPDATED);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addListener(listener);
		db.setTransportConfig("foo", config1);

		context.assertIsSatisfied();
	}

	@Test
	public void testTransportConfigUnchangedDoesNotCallListeners()
	throws Exception {
		final Map<String, String> config =
			Collections.singletonMap("bar", "baz");
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getTransportConfig(txn, "foo");
			will(returnValue(config));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.addListener(listener);
		db.setTransportConfig("foo", config);

		context.assertIsSatisfied();
	}

	@Test
	public void testSetSeen() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// setSeen(contactId, Collections.singleton(messageId))
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner);

		db.setSeen(contactId, Collections.singleton(messageId));

		context.assertIsSatisfied();
	}
}
