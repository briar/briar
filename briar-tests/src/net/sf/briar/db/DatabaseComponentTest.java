package net.sf.briar.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.NoSuchContactTransportException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessagesAddedEvent;
import net.sf.briar.api.db.event.RatingChangedEvent;
import net.sf.briar.api.db.event.SubscriptionsUpdatedEvent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.ContactTransport;
import net.sf.briar.api.transport.TemporarySecret;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public abstract class DatabaseComponentTest extends BriarTestCase {

	protected final Object txn = new Object();
	protected final AuthorId authorId;
	protected final BatchId batchId;
	protected final ContactId contactId;
	protected final GroupId groupId;
	protected final MessageId messageId, parentId;
	private final String subject;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message, privateMessage;
	private final Group group;
	private final TransportId transportId;
	private final Collection<Transport> transports;
	private final ContactTransport contactTransport;
	private final TemporarySecret temporarySecret;

	public DatabaseComponentTest() {
		super();
		authorId = new AuthorId(TestUtils.getRandomId());
		batchId = new BatchId(TestUtils.getRandomId());
		contactId = new ContactId(234);
		groupId = new GroupId(TestUtils.getRandomId());
		messageId = new MessageId(TestUtils.getRandomId());
		parentId = new MessageId(TestUtils.getRandomId());
		subject = "Foo";
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		message = new TestMessage(messageId, null, groupId, authorId, subject,
				timestamp, raw);
		privateMessage = new TestMessage(messageId, null, null, null, subject,
				timestamp, raw);
		group = new Group(groupId, "The really exciting group", null);
		transportId = new TransportId(TestUtils.getRandomId());
		TransportProperties properties = new TransportProperties(
				Collections.singletonMap("foo", "bar"));
		Transport transport = new Transport(transportId, properties);
		transports = Collections.singletonList(transport);
		contactTransport = new ContactTransport(contactId, transportId, 123L,
				234L, 345L, true);
		temporarySecret = new TemporarySecret(contactId, transportId, 1L, 2L,
				3L, false, 4L, new byte[32], 5L, 6L, new byte[4]);
	}

	protected abstract <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner,
			ShutdownManager shutdown, PacketFactory packetFactory);

	@Test
	@SuppressWarnings("unchecked")
	public void testSimpleCalls() throws Exception {
		final int shutdownHandle = 12345;
		Mockery context = new Mockery();
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
			oneOf(shutdown).addShutdownHook(with(any(Runnable.class)));
			will(returnValue(shutdownHandle));
			// getRating(authorId)
			oneOf(database).getRating(txn, authorId);
			will(returnValue(Rating.UNRATED));
			// setRating(authorId, Rating.GOOD)
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			will(returnValue(Rating.UNRATED));
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.emptyList()));
			oneOf(listener).eventOccurred(with(any(RatingChangedEvent.class)));
			// setRating(authorId, Rating.GOOD) again
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			will(returnValue(Rating.GOOD));
			// addContact()
			oneOf(database).addContact(txn);
			will(returnValue(contactId));
			oneOf(listener).eventOccurred(with(any(ContactAddedEvent.class)));
			// getContacts()
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contactId)));
			// getTransportProperties(transportId)
			oneOf(database).getRemoteProperties(txn, transportId);
			will(returnValue(Collections.emptyMap()));
			// subscribe(group)
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			oneOf(database).addSubscription(txn, group);
			// subscribe(group) again
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			// getMessageHeaders(groupId)
			oneOf(database).getMessageHeaders(txn, groupId);
			will(returnValue(Collections.emptyList()));
			// getSubscriptions()
			oneOf(database).getSubscriptions(txn);
			will(returnValue(Collections.singletonList(groupId)));
			// unsubscribe(groupId)
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Collections.emptyList()));
			oneOf(database).removeSubscription(txn, groupId);
			// unsubscribe(groupId) again
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			// removeContact(contactId)
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).removeContact(txn, contactId);
			oneOf(listener).eventOccurred(with(any(ContactRemovedEvent.class)));
			// close()
			oneOf(shutdown).removeShutdownHook(shutdownHandle);
			oneOf(cleaner).stopCleaning();
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.open(false);
		db.addListener(listener);
		assertEquals(Rating.UNRATED, db.getRating(authorId));
		db.setRating(authorId, Rating.GOOD); // First time - listeners called
		db.setRating(authorId, Rating.GOOD); // Second time - not called
		assertEquals(contactId, db.addContact());
		assertEquals(Collections.singletonList(contactId), db.getContacts());
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(transportId));
		db.subscribe(group); // First time - listeners called
		db.subscribe(group); // Second time - not called
		assertEquals(Collections.emptyList(), db.getMessageHeaders(groupId));
		assertEquals(Collections.singletonList(groupId), db.getSubscriptions());
		db.unsubscribe(groupId); // First time - listeners called
		db.unsubscribe(groupId); // Second time - not called
		db.removeContact(contactId);
		db.removeListener(listener);
		db.close();

		context.assertIsSatisfied();
	}

	@Test
	public void testNullParentStopsBackwardInclusion() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			// setRating(authorId, Rating.GOOD)
			allowing(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			will(returnValue(Rating.UNRATED));
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// Backward inclusion stops when the message has no parent
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testUnaffectedParentStopsBackwardInclusion() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			// setRating(authorId, Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			will(returnValue(Rating.UNRATED));
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists, is in the DB, and is in the same group
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(parentId));
			// The parent is already sendable
			oneOf(database).getSendability(txn, parentId);
			will(returnValue(1));
			oneOf(database).setSendability(txn, parentId, 2);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testAffectedParentContinuesBackwardInclusion()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			// setRating(authorId, Rating.GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, Rating.GOOD);
			will(returnValue(Rating.UNRATED));
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.singletonList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists, is in the DB, and is in the same group
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(parentId));
			// The parent is not already sendable
			oneOf(database).getSendability(txn, parentId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, parentId, 1);
			// The parent has no parent
			oneOf(database).getGroupMessageParent(txn, parentId);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.setRating(authorId, Rating.GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testGroupMessagesAreNotStoredUnlessSubscribed()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId, timestamp);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicateGroupMessagesAreNotStored() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocalGroupMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingSendableMessageTriggersBackwardInclusion()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addLocalGroupMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicatePrivateMessagesAreNotStored() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addLocalPrivateMessage(privateMessage, contactId);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocalPrivateMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final Ack ack = context.mock(Ack.class);
		final Batch batch = context.mock(Batch.class);
		final Offer offer = context.mock(Offer.class);
		final SubscriptionUpdate subscriptionUpdate =
				context.mock(SubscriptionUpdate.class);
		final TransportUpdate transportUpdate =
				context.mock(TransportUpdate.class);
		context.checking(new Expectations() {{
			// Check whether the contact is in the DB (which it's not)
			exactly(16).of(database).startTransaction();
			will(returnValue(txn));
			exactly(16).of(database).containsContact(txn, contactId);
			will(returnValue(false));
			exactly(16).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		try {
			db.addContactTransport(contactTransport);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.addLocalPrivateMessage(privateMessage, contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateAck(contactId, 123);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateBatch(contactId, 123);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateBatch(contactId, 123,
					Collections.<MessageId>emptyList());
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateOffer(contactId, 123);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateSubscriptionUpdate(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateTransportUpdate(contactId);
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
			db.receiveOffer(contactId, offer);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveSubscriptionUpdate(contactId, subscriptionUpdate);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveTransportUpdate(contactId, transportUpdate);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.removeContact(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.setSeen(contactId, Collections.singletonList(messageId));
			fail();
		} catch(NoSuchContactException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfContactTransportIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			// Check whether the contact transport is in the DB (which it's not)
			exactly(2).of(database).startTransaction();
			will(returnValue(txn));
			exactly(2).of(database).containsContactTransport(txn, contactId,
					transportId);
			will(returnValue(false));
			exactly(2).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		try {
			db.incrementConnectionCounter(contactId, transportId, 0L);
			fail();
		} catch(NoSuchContactTransportException expected) {}

		try {
			db.setConnectionWindow(contactId, transportId, 0L, 0L, new byte[4]);
			fail();
		} catch(NoSuchContactTransportException expected) {}

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final Ack ack = context.mock(Ack.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the batches to ack
			oneOf(database).getBatchesToAck(txn, contactId, 123);
			will(returnValue(batchesToAck));
			// Create the packet
			oneOf(packetFactory).createAck(batchesToAck);
			will(returnValue(ack));
			// Record the batches that were acked
			oneOf(database).removeBatchesToAck(txn, contactId, batchesToAck);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		assertEquals(ack, db.generateAck(contactId, 123));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateBatch() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final byte[] raw1 = new byte[size];
		final Collection<MessageId> sendable = Arrays.asList(messageId,
				messageId1);
		final Collection<byte[]> messages = Arrays.asList(raw, raw1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final RawBatch batch = context.mock(RawBatch.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the sendable messages
			oneOf(database).getSendableMessages(txn, contactId, size * 2);
			will(returnValue(sendable));
			oneOf(database).getMessage(txn, messageId);
			will(returnValue(raw));
			oneOf(database).getMessage(txn, messageId1);
			will(returnValue(raw1));
			// Create the packet
			oneOf(packetFactory).createBatch(messages);
			will(returnValue(batch));
			// Record the outstanding batch
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addOutstandingBatch(txn, contactId, batchId,
					sendable);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		assertEquals(batch, db.generateBatch(contactId, size * 2));

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
		final Collection<byte[]> msgs = Arrays.asList(raw1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final RawBatch batch = context.mock(RawBatch.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Try to get the requested messages
			oneOf(database).getMessageIfSendable(txn, contactId, messageId);
			will(returnValue(null)); // Message is not sendable
			oneOf(database).getMessageIfSendable(txn, contactId, messageId1);
			will(returnValue(raw1)); // Message is sendable
			oneOf(database).getMessageIfSendable(txn, contactId, messageId2);
			will(returnValue(null)); // Message is not sendable
			// Create the packet
			oneOf(packetFactory).createBatch(msgs);
			will(returnValue(batch));
			// Record the outstanding batch
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addOutstandingBatch(txn, contactId, batchId,
					Collections.singletonList(messageId1));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		assertEquals(batch, db.generateBatch(contactId, size * 3, requested));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateOffer() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> offerable = new ArrayList<MessageId>();
		offerable.add(messageId);
		offerable.add(messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final Offer offer = context.mock(Offer.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the sendable message IDs
			oneOf(database).getOfferableMessages(txn, contactId, 123);
			will(returnValue(offerable));
			// Create the packet
			oneOf(packetFactory).createOffer(offerable);
			will(returnValue(offer));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		assertEquals(offer, db.generateOffer(contactId, 123));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateSubscriptionUpdate() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final SubscriptionUpdate subscriptionUpdate =
				context.mock(SubscriptionUpdate.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the visible holes and subscriptions
			oneOf(database).getVisibleHoles(with(txn), with(contactId),
					with(any(long.class)));
			will(returnValue(Collections.emptyMap()));
			oneOf(database).getVisibleSubscriptions(with(txn), with(contactId),
					with(any(long.class)));
			will(returnValue(Collections.singletonMap(group, 0L)));
			// Get the expiry time
			oneOf(database).getExpiryTime(txn);
			will(returnValue(0L));
			// Create the packet
			oneOf(packetFactory).createSubscriptionUpdate(
					with(Collections.<GroupId, GroupId>emptyMap()),
					with(Collections.singletonMap(group, 0L)),
					with(any(long.class)),
					with(any(long.class)));
			will(returnValue(subscriptionUpdate));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		assertEquals(subscriptionUpdate,
				db.generateSubscriptionUpdate(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testTransportUpdateNotSentUnlessDue() throws Exception {
		final long now = System.currentTimeMillis();
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Check whether an update is due
			oneOf(database).getTransportsModified(txn);
			will(returnValue(now - 1L));
			oneOf(database).getTransportsSent(txn, contactId);
			will(returnValue(now));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		assertNull(db.generateTransportUpdate(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateTransportUpdate() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final TransportUpdate transportUpdate =
				context.mock(TransportUpdate.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Check whether an update is due
			oneOf(database).getTransportsModified(txn);
			will(returnValue(0L));
			oneOf(database).getTransportsSent(txn, contactId);
			will(returnValue(0L));
			// Get the local transport properties
			oneOf(database).getLocalTransports(txn);
			will(returnValue(transports));
			oneOf(database).setTransportsSent(with(txn), with(contactId),
					with(any(long.class)));
			// Create the packet
			oneOf(packetFactory).createTransportUpdate(with(transports),
					with(any(long.class)));
			will(returnValue(transportUpdate));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		assertEquals(transportUpdate, db.generateTransportUpdate(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveAck() throws Exception {
		final BatchId batchId1 = new BatchId(TestUtils.getRandomId());
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.receiveAck(contactId, ack);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchStoresPrivateMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchWithDuplicatePrivateMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchCalculatesSendability() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.receiveBatch(contactId, batch);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveBatchUpdatesAncestorSendability() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(null));
			// The batch needs to be acknowledged
			oneOf(batch).getId();
			will(returnValue(batchId));
			oneOf(database).addBatchToAck(txn, contactId, batchId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final Offer offer = context.mock(Offer.class);
		final Request request = context.mock(Request.class);
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
			// Create the packet
			oneOf(packetFactory).createRequest(expectedRequest, 3);
			will(returnValue(request));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		assertEquals(request, db.receiveOffer(contactId, offer));

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveSubscriptionUpdate() throws Exception {
		final GroupId start = new GroupId(TestUtils.getRandomId());
		final GroupId end = new GroupId(TestUtils.getRandomId());
		final long expiry = 1234L, timestamp = 5678L;
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final SubscriptionUpdate subscriptionUpdate =
				context.mock(SubscriptionUpdate.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the contents of the update
			oneOf(subscriptionUpdate).getHoles();
			will(returnValue(Collections.singletonMap(start, end)));
			oneOf(subscriptionUpdate).getSubscriptions();
			will(returnValue(Collections.singletonMap(group, 0L)));
			oneOf(subscriptionUpdate).getExpiryTime();
			will(returnValue(expiry));
			oneOf(subscriptionUpdate).getTimestamp();
			will(returnValue(timestamp));
			// Store the contents of the update
			oneOf(database).removeSubscriptions(txn, contactId, start, end);
			oneOf(database).addSubscription(txn, contactId, group, 0L);
			oneOf(database).setExpiryTime(txn, contactId, expiry);
			oneOf(database).setSubscriptionsReceived(txn, contactId, timestamp);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.receiveTransportUpdate(contactId, transportUpdate);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingGroupMessageCallsListeners() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
			oneOf(listener).eventOccurred(with(any(MessagesAddedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
			oneOf(listener).eventOccurred(with(any(MessagesAddedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
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
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addListener(listener);
		db.addLocalPrivateMessage(privateMessage, contactId);

		context.assertIsSatisfied();
	}

	@Test
	public void testTransportPropertiesChangedCallsListeners()
			throws Exception {
		final TransportProperties properties =
				new TransportProperties(Collections.singletonMap("bar", "baz"));
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getLocalProperties(txn, transportId);
			will(returnValue(new TransportProperties()));
			oneOf(database).mergeLocalProperties(txn, transportId, properties);
			oneOf(database).setTransportsModified(with(txn),
					with(any(long.class)));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.mergeLocalProperties(transportId, properties);

		context.assertIsSatisfied();
	}

	@Test
	public void testTransportPropertiesUnchangedDoesNotCallListeners()
			throws Exception {
		final TransportProperties properties =
				new TransportProperties(Collections.singletonMap("bar", "baz"));
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getLocalProperties(txn, transportId);
			will(returnValue(properties));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addListener(listener);
		db.mergeLocalProperties(transportId, properties);

		context.assertIsSatisfied();
	}

	@Test
	public void testSetSeen() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			allowing(database).startTransaction();
			will(returnValue(txn));
			allowing(database).commitTransaction(txn);
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			// setSeen(contactId, Collections.singletonList(messageId))
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.setSeen(contactId, Collections.singletonList(messageId));

		context.assertIsSatisfied();
	}

	@Test
	public void testVisibilityChangedCallsListeners() throws Exception {
		final ContactId contactId1 = new ContactId(123);
		final Collection<ContactId> both = Arrays.asList(contactId, contactId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(both));
			oneOf(database).getContacts(txn);
			will(returnValue(both));
			oneOf(database).removeVisibility(txn, contactId1, groupId);
			oneOf(database).commitTransaction(txn);
			oneOf(listener).eventOccurred(with(any(
					SubscriptionsUpdatedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addListener(listener);
		db.setVisibility(groupId, Collections.singletonList(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testVisibilityUnchangedDoesNotCallListeners() throws Exception {
		final ContactId contactId1 = new ContactId(234);
		final Collection<ContactId> both = Arrays.asList(contactId, contactId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(both));
			oneOf(database).getContacts(txn);
			will(returnValue(both));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addListener(listener);
		db.setVisibility(groupId, both);

		context.assertIsSatisfied();
	}

	@Test
	public void testTemporarySecrets() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		context.checking(new Expectations() {{
			// addSecrets()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContactTransport(txn, contactId,
					transportId);
			will(returnValue(true));
			oneOf(database).addSecrets(txn,
					Collections.singletonList(temporarySecret));
			oneOf(database).commitTransaction(txn);
			// getSecrets()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getSecrets(txn);
			will(returnValue(Collections.singletonList(temporarySecret)));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown, packetFactory);

		db.addSecrets(Collections.singletonList(temporarySecret));
		assertEquals(Collections.singletonList(temporarySecret),
				db.getSecrets());

		context.assertIsSatisfied();
	}
}
