package net.sf.briar.db;

import static net.sf.briar.api.Rating.GOOD;
import static net.sf.briar.api.Rating.UNRATED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestMessage;
import net.sf.briar.TestUtils;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.NoSuchSubscriptionException;
import net.sf.briar.api.db.NoSuchTransportException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.GroupMessageAddedEvent;
import net.sf.briar.api.db.event.LocalSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.PrivateMessageAddedEvent;
import net.sf.briar.api.db.event.RatingChangedEvent;
import net.sf.briar.api.db.event.SubscriptionAddedEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.AuthorId;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.api.transport.TemporarySecret;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public abstract class DatabaseComponentTest extends BriarTestCase {

	protected final Object txn = new Object();
	protected final GroupId groupId;
	protected final Group group;
	protected final AuthorId authorId;
	protected final Author author;
	protected final MessageId messageId, messageId1;
	protected final String contentType, subject;
	protected final long timestamp;
	protected final int size;
	protected final byte[] raw;
	protected final Message message, privateMessage;
	protected final TransportId transportId;
	protected final TransportProperties transportProperties;
	protected final ContactId contactId;
	protected final String contactName;
	protected final Contact contact;
	protected final Endpoint endpoint;
	protected final TemporarySecret temporarySecret;

	public DatabaseComponentTest() {
		super();
		groupId = new GroupId(TestUtils.getRandomId());
		group = new Group(groupId, "Group name", null);
		authorId = new AuthorId(TestUtils.getRandomId());
		author = new Author(authorId, "Alice", new byte[60]);
		messageId = new MessageId(TestUtils.getRandomId());
		messageId1 = new MessageId(TestUtils.getRandomId());
		contentType = "text/plain";
		subject = "Foo";
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		message = new TestMessage(messageId, null, group, author, contentType,
				subject, timestamp, raw);
		privateMessage = new TestMessage(messageId, null, null, null,
				contentType, subject, timestamp, raw);
		transportId = new TransportId(TestUtils.getRandomId());
		transportProperties = new TransportProperties(Collections.singletonMap(
				"foo", "bar"));
		contactId = new ContactId(234);
		contactName = "Alice";
		contact = new Contact(contactId, contactName, timestamp);
		endpoint = new Endpoint(contactId, transportId, 123, 234, 345, true);
		temporarySecret = new TemporarySecret(contactId, transportId, 1, 2,
				3, false, 4, new byte[32], 5, 6, new byte[4]);
	}

	protected abstract <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner,
			ShutdownManager shutdown);

	@Test
	@SuppressWarnings("unchecked")
	public void testSimpleCalls() throws Exception {
		final int shutdownHandle = 12345;
		Mockery context = new Mockery();
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			exactly(12).of(database).startTransaction();
			will(returnValue(txn));
			exactly(12).of(database).commitTransaction(txn);
			// open(false)
			oneOf(database).open(false);
			oneOf(cleaner).startCleaning(
					with(any(DatabaseCleaner.Callback.class)),
					with(any(long.class)));
			oneOf(shutdown).addShutdownHook(with(any(Runnable.class)));
			will(returnValue(shutdownHandle));
			// getRating(authorId)
			oneOf(database).getRating(txn, authorId);
			will(returnValue(UNRATED));
			// setRating(authorId, GOOD)
			oneOf(database).setRating(txn, authorId, GOOD);
			will(returnValue(UNRATED));
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Collections.emptyList()));
			oneOf(listener).eventOccurred(with(any(RatingChangedEvent.class)));
			// setRating(authorId, GOOD) again
			oneOf(database).setRating(txn, authorId, GOOD);
			will(returnValue(GOOD));
			// addContact(contactName)
			oneOf(database).addContact(txn, contactName);
			will(returnValue(contactId));
			oneOf(listener).eventOccurred(with(any(ContactAddedEvent.class)));
			// getContacts()
			oneOf(database).getContacts(txn);
			will(returnValue(Arrays.asList(contact)));
			// getRemoteProperties(transportId)
			oneOf(database).getRemoteProperties(txn, transportId);
			will(returnValue(Collections.emptyMap()));
			// subscribe(group)
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			oneOf(database).addSubscription(txn, group);
			will(returnValue(true));
			oneOf(listener).eventOccurred(with(any(
					SubscriptionAddedEvent.class)));
			// subscribe(group) again
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			// getMessageHeaders(groupId)
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).getMessageHeaders(txn, groupId);
			will(returnValue(Collections.emptyList()));
			// getSubscriptions()
			oneOf(database).getSubscriptions(txn);
			will(returnValue(Arrays.asList(groupId)));
			// unsubscribe(groupId)
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Collections.emptyList()));
			oneOf(database).removeSubscription(txn, groupId);
			oneOf(listener).eventOccurred(with(any(
					SubscriptionRemovedEvent.class)));
			oneOf(listener).eventOccurred(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
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
				shutdown);

		db.open(false);
		db.addListener(listener);
		assertEquals(UNRATED, db.getRating(authorId));
		db.setRating(authorId, GOOD); // First time - listeners called
		db.setRating(authorId, GOOD); // Second time - not called
		assertEquals(contactId, db.addContact(contactName));
		assertEquals(Arrays.asList(contact), db.getContacts());
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(transportId));
		db.subscribe(group); // First time - listeners called
		db.subscribe(group); // Second time - not called
		assertEquals(Collections.emptyList(), db.getMessageHeaders(groupId));
		assertEquals(Arrays.asList(groupId), db.getSubscriptions());
		db.unsubscribe(groupId); // Listeners called
		db.removeContact(contactId); // Listeners called
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
		context.checking(new Expectations() {{
			// setRating(authorId, GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, GOOD);
			will(returnValue(UNRATED));
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Arrays.asList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// Backward inclusion stops when the message has no parent
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.setRating(authorId, GOOD);

		context.assertIsSatisfied();
	}

	@Test
	public void testUnaffectedParentStopsBackwardInclusion() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			// setRating(authorId, GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, GOOD);
			will(returnValue(UNRATED));
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Arrays.asList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists, is in the DB, and is in the same group
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(messageId1));
			// The parent is already sendable
			oneOf(database).getSendability(txn, messageId1);
			will(returnValue(1));
			oneOf(database).setSendability(txn, messageId1, 2);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.setRating(authorId, GOOD);

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
		context.checking(new Expectations() {{
			// setRating(authorId, GOOD)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).setRating(txn, authorId, GOOD);
			will(returnValue(UNRATED));
			// The sendability of the author's messages should be incremented
			oneOf(database).getMessagesByAuthor(txn, authorId);
			will(returnValue(Arrays.asList(messageId)));
			oneOf(database).getSendability(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 1);
			// The parent exists, is in the DB, and is in the same group
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(messageId1));
			// The parent is not already sendable
			oneOf(database).getSendability(txn, messageId1);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId1, 1);
			// The parent has no parent
			oneOf(database).getGroupMessageParent(txn, messageId1);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.setRating(authorId, GOOD);

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
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(true));
			oneOf(database).setReadFlag(txn, messageId, true);
			oneOf(database).getContactIds(txn);
			will(returnValue(Arrays.asList(contactId)));
			oneOf(database).addStatus(txn, contactId, messageId, false);
			// The author is unrated and there are no sendable children
			oneOf(database).getRating(txn, authorId);
			will(returnValue(UNRATED));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 0);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(true));
			oneOf(database).setReadFlag(txn, messageId, true);
			oneOf(database).getContactIds(txn);
			will(returnValue(Arrays.asList(contactId)));
			oneOf(database).addStatus(txn, contactId, messageId, false);
			// The author is rated GOOD and there are two sendable children
			oneOf(database).getRating(txn, authorId);
			will(returnValue(GOOD));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(2));
			oneOf(database).setSendability(txn, messageId, 3);
			// The sendability of the message's ancestors should be updated
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// addLocalPrivateMessage(privateMessage, contactId)
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(false));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// addLocalPrivateMessage(privateMessage, contactId)
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(true));
			oneOf(database).setReadFlag(txn, messageId, true);
			oneOf(database).addStatus(txn, contactId, messageId, false);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		context.checking(new Expectations() {{
			// Check whether the contact is in the DB (which it's not)
			exactly(28).of(database).startTransaction();
			will(returnValue(txn));
			exactly(28).of(database).containsContact(txn, contactId);
			will(returnValue(false));
			exactly(28).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		try {
			db.addEndpoint(endpoint);
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
			db.generateBatch(contactId, 123, 456);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateBatch(contactId, 123, 456, Arrays.asList(messageId));
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateOffer(contactId, 123);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateRetentionAck(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateRetentionUpdate(contactId, 123);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateSubscriptionAck(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateSubscriptionUpdate(contactId, 123);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateTransportAcks(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.generateTransportUpdates(contactId, 123);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.getContact(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.getVisibleSubscriptions(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.hasSendableMessages(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.incrementConnectionCounter(contactId, transportId, 0);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			Ack a = new Ack(Arrays.asList(messageId));
			db.receiveAck(contactId, a);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.receiveMessage(contactId, message);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			Offer o = new Offer(Arrays.asList(messageId));
			db.receiveOffer(contactId, o);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			RetentionAck a = new RetentionAck(0);
			db.receiveRetentionAck(contactId, a);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			RetentionUpdate u = new RetentionUpdate(0, 1);
			db.receiveRetentionUpdate(contactId, u);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			SubscriptionAck a = new SubscriptionAck(0);
			db.receiveSubscriptionAck(contactId, a);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			SubscriptionUpdate u = new SubscriptionUpdate(
					Collections.<Group>emptyList(), 1);
			db.receiveSubscriptionUpdate(contactId, u);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			TransportAck a = new TransportAck(transportId, 0);
			db.receiveTransportAck(contactId, a);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			TransportUpdate u = new TransportUpdate(transportId,
					transportProperties, 1);
			db.receiveTransportUpdate(contactId, u);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.removeContact(contactId);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.setConnectionWindow(contactId, transportId, 0, 0, new byte[4]);
			fail();
		} catch(NoSuchContactException expected) {}

		try {
			db.setSeen(contactId, Arrays.asList(messageId));
			fail();
		} catch(NoSuchContactException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfSubscriptionIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			// Check whether the subscription is in the DB (which it's not)
			exactly(5).of(database).startTransaction();
			will(returnValue(txn));
			exactly(5).of(database).containsTransport(txn, transportId);
			will(returnValue(false));
			exactly(5).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		try {
			db.getGroup(groupId);
			fail();
		} catch(NoSuchSubscriptionException expected) {}

		try {
			db.getMessageHeaders(groupId);
			fail();
		} catch(NoSuchSubscriptionException expected) {}

		try {
			db.getVisibility(groupId);
			fail();
		} catch(NoSuchSubscriptionException expected) {}

		try {
			db.setVisibility(groupId, Collections.<ContactId>emptyList());
			fail();
		} catch(NoSuchSubscriptionException expected) {}

		try {
			db.unsubscribe(groupId);
			fail();
		} catch(NoSuchSubscriptionException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfTransportIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			// addContact()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).addContact(txn, contactName);
			will(returnValue(contactId));
			oneOf(database).commitTransaction(txn);
			// Check whether the transport is in the DB (which it's not)
			exactly(8).of(database).startTransaction();
			will(returnValue(txn));
			exactly(3).of(database).containsContact(txn, contactId);
			will(returnValue(true));
			exactly(8).of(database).containsTransport(txn, transportId);
			will(returnValue(false));
			exactly(8).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);
		assertEquals(contactId, db.addContact(contactName));

		try {
			db.addEndpoint(endpoint);
			fail();
		} catch(NoSuchTransportException expected) {}

		try {
			db.getConfig(transportId);
			fail();
		} catch(NoSuchTransportException expected) {}

		try {
			db.getLocalProperties(transportId);
			fail();
		} catch(NoSuchTransportException expected) {}

		try {
			db.mergeConfig(transportId, new TransportConfig());
			fail();
		} catch(NoSuchTransportException expected) {}

		try {
			db.mergeLocalProperties(transportId, new TransportProperties());
			fail();
		} catch(NoSuchTransportException expected) {}

		try {
			db.incrementConnectionCounter(contactId, transportId, 0);
			fail();
		} catch(NoSuchTransportException expected) {}

		try {
			db.removeTransport(transportId);
			fail();
		} catch(NoSuchTransportException expected) {}

		try {
			db.setConnectionWindow(contactId, transportId, 0, 0, new byte[4]);
			fail();
		} catch(NoSuchTransportException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateAck() throws Exception {
		final Collection<MessageId> messagesToAck = Arrays.asList(messageId,
				messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			// Two transactions: read and write
			exactly(2).of(database).startTransaction();
			will(returnValue(txn));
			exactly(2).of(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the messages to ack
			oneOf(database).getMessagesToAck(txn, contactId, 123);
			will(returnValue(messagesToAck));
			// Record the messages that were acked
			oneOf(database).removeMessagesToAck(txn, contactId, messagesToAck);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		Ack a = db.generateAck(contactId, 123);
		assertEquals(messagesToAck, a.getMessageIds());

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateBatch() throws Exception {
		final byte[] raw1 = new byte[size];
		final Collection<MessageId> sendable = Arrays.asList(messageId,
				messageId1);
		final Collection<byte[]> messages = Arrays.asList(raw, raw1);
		final Map<MessageId, Integer> sent = new HashMap<MessageId, Integer>();
		sent.put(messageId, 1);
		sent.put(messageId1, 2);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			// Two transactions: read and write
			exactly(2).of(database).startTransaction();
			will(returnValue(txn));
			exactly(2).of(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the sendable messages and their transmission counts
			oneOf(database).getSendableMessages(txn, contactId, size * 2);
			will(returnValue(sendable));
			oneOf(database).getRawMessage(txn, messageId);
			will(returnValue(raw));
			oneOf(database).getTransmissionCount(txn, contactId, messageId);
			will(returnValue(1));
			oneOf(database).getRawMessage(txn, messageId1);
			will(returnValue(raw1));
			oneOf(database).getTransmissionCount(txn, contactId, messageId1);
			will(returnValue(2));
			// Record the outstanding messages
			oneOf(database).updateExpiryTimes(txn, contactId, sent,
					Long.MAX_VALUE);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		assertEquals(messages, db.generateBatch(contactId, size * 2,
				Long.MAX_VALUE));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateBatchFromRequest() throws Exception {
		final MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		final byte[] raw1 = new byte[size];
		final Collection<MessageId> requested = new ArrayList<MessageId>(
				Arrays.asList(messageId, messageId1, messageId2));
		final Collection<byte[]> messages = Arrays.asList(raw1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			// Two transactions: read and write
			exactly(2).of(database).startTransaction();
			will(returnValue(txn));
			exactly(2).of(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Try to get the requested messages
			oneOf(database).getRawMessageIfSendable(txn, contactId, messageId);
			will(returnValue(null)); // Message is not sendable
			oneOf(database).getRawMessageIfSendable(txn, contactId, messageId1);
			will(returnValue(raw1)); // Message is sendable
			oneOf(database).getTransmissionCount(txn, contactId, messageId1);
			will(returnValue(2));
			oneOf(database).getRawMessageIfSendable(txn, contactId, messageId2);
			will(returnValue(null)); // Message is not sendable
			// Mark the message as sent
			oneOf(database).updateExpiryTimes(txn, contactId,
					Collections.singletonMap(messageId1, 2), Long.MAX_VALUE);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		assertEquals(messages, db.generateBatch(contactId, size * 3,
				Long.MAX_VALUE, requested));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateOffer() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> messagesToOffer = Arrays.asList(messageId,
				messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the sendable message IDs
			oneOf(database).getMessagesToOffer(txn, contactId, 123);
			will(returnValue(messagesToOffer));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		Offer o = db.generateOffer(contactId, 123);
		assertEquals(messagesToOffer, o.getMessageIds());

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateRetentionUpdateNoUpdateDue() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getRetentionUpdate(txn, contactId, Long.MAX_VALUE);
			will(returnValue(null));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		assertNull(db.generateRetentionUpdate(contactId, Long.MAX_VALUE));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateRetentionUpdate() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getRetentionUpdate(txn, contactId, Long.MAX_VALUE);
			will(returnValue(new RetentionUpdate(0, 1)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		RetentionUpdate u = db.generateRetentionUpdate(contactId,
				Long.MAX_VALUE);
		assertEquals(0, u.getRetentionTime());
		assertEquals(1, u.getVersion());

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateSubscriptionUpdateNoUpdateDue() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getSubscriptionUpdate(txn, contactId,
					Long.MAX_VALUE);
			will(returnValue(null));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		assertNull(db.generateSubscriptionUpdate(contactId, Long.MAX_VALUE));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateSubscriptionUpdate() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getSubscriptionUpdate(txn, contactId,
					Long.MAX_VALUE);
			will(returnValue(new SubscriptionUpdate(Arrays.asList(group), 1)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		SubscriptionUpdate u = db.generateSubscriptionUpdate(contactId,
				Long.MAX_VALUE);
		assertEquals(Arrays.asList(group), u.getGroups());
		assertEquals(1, u.getVersion());

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateTransportUpdatesNoUpdatesDue() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransportUpdates(txn, contactId, Long.MAX_VALUE);
			will(returnValue(null));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		assertNull(db.generateTransportUpdates(contactId, Long.MAX_VALUE));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateTransportUpdates() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransportUpdates(txn, contactId, Long.MAX_VALUE);
			will(returnValue(Arrays.asList(new TransportUpdate(transportId,
					transportProperties, 1))));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		Collection<TransportUpdate> updates =
				db.generateTransportUpdates(contactId, Long.MAX_VALUE);
		assertNotNull(updates);
		assertEquals(1, updates.size());
		TransportUpdate u = updates.iterator().next();
		assertEquals(transportId, u.getId());
		assertEquals(transportProperties, u.getProperties());
		assertEquals(1, u.getVersion());

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveAck() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the acked messages
			oneOf(database).removeOutstandingMessages(txn, contactId,
					Arrays.asList(messageId));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveAck(contactId, new Ack(Arrays.asList(messageId)));

		context.assertIsSatisfied();
	}

	@Test
	public void testReceivePrivateMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// The message is stored
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(true));
			oneOf(database).addStatus(txn, contactId, messageId, true);
			// The message must be acked
			oneOf(database).addMessageToAck(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveMessage(contactId, privateMessage);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveDuplicatePrivateMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// The message is stored, but it's a duplicate
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(false));
			// The message must still be acked
			oneOf(database).addMessageToAck(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveMessage(contactId, privateMessage);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveMessageDoesNotStoreGroupMessageUnlessSubscribed()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Only store messages belonging to visible, subscribed groups
			oneOf(database).containsVisibleSubscription(txn, contactId,
					groupId);
			will(returnValue(false));
			// The message is not stored but it must still be acked
			oneOf(database).addMessageToAck(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveMessage(contactId, message);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveMessageDoesNotCalculateSendabilityForDuplicates()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Only store messages belonging to visible, subscribed groups
			oneOf(database).containsVisibleSubscription(txn, contactId,
					groupId);
			will(returnValue(true));
			// The message is stored, but it's a duplicate
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(false));
			oneOf(database).addStatus(txn, contactId, messageId, true);
			// The message must be acked
			oneOf(database).addMessageToAck(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveMessage(contactId, message);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveMessageCalculatesSendability() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Only store messages belonging to visible, subscribed groups
			oneOf(database).containsVisibleSubscription(txn, contactId,
					groupId);
			will(returnValue(true));
			// The message is stored, and it's not a duplicate
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(true));
			oneOf(database).addStatus(txn, contactId, messageId, true);
			// Set the status to seen = true for all other contacts (none)
			oneOf(database).getContactIds(txn);
			will(returnValue(Arrays.asList(contactId)));
			// Calculate the sendability - zero, so ancestors aren't updated
			oneOf(database).getRating(txn, authorId);
			will(returnValue(UNRATED));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 0);
			// The message must be acked
			oneOf(database).addMessageToAck(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveMessage(contactId, message);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveMessageUpdatesAncestorSendability()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Only store messages belonging to visible, subscribed groups
			oneOf(database).containsVisibleSubscription(txn, contactId,
					groupId);
			will(returnValue(true));
			// The message is stored, and it's not a duplicate
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(true));
			oneOf(database).addStatus(txn, contactId, messageId, true);
			// Set the status to seen = true for all other contacts (none)
			oneOf(database).getContactIds(txn);
			will(returnValue(Arrays.asList(contactId)));
			// Calculate the sendability - ancestors are updated
			oneOf(database).getRating(txn, authorId);
			will(returnValue(GOOD));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(1));
			oneOf(database).setSendability(txn, messageId, 2);
			oneOf(database).getGroupMessageParent(txn, messageId);
			will(returnValue(null));
			// The message must be acked
			oneOf(database).addMessageToAck(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveMessage(contactId, message);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveOffer() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		final BitSet expectedRequest = new BitSet(3);
		expectedRequest.set(0);
		expectedRequest.set(2);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// Get the offered messages
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId);
			will(returnValue(false)); // Not visible - request message # 0
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId1);
			will(returnValue(true)); // Visible - do not request message # 1
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId2);
			will(returnValue(false)); // Not visible - request message # 2
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		Offer o = new Offer(Arrays.asList(messageId, messageId1, messageId2));
		Request r = db.receiveOffer(contactId, o);
		assertEquals(expectedRequest, r.getBitmap());
		assertEquals(3, r.getLength());

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveRetentionAck() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setRetentionUpdateAcked(txn, contactId, 1);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		RetentionAck a = new RetentionAck(1);
		db.receiveRetentionAck(contactId, a);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveSubscriptionAck() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setSubscriptionUpdateAcked(txn, contactId, 1);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		SubscriptionAck a = new SubscriptionAck(1);
		db.receiveSubscriptionAck(contactId, a);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveSubscriptionUpdate() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setSubscriptions(txn, contactId,
					Arrays.asList(group), 1);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		SubscriptionUpdate u = new SubscriptionUpdate(Arrays.asList(group), 1);
		db.receiveSubscriptionUpdate(contactId, u);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveTransportAck() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).setTransportUpdateAcked(txn, contactId,
					transportId, 1);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		TransportAck a = new TransportAck(transportId, 1);
		db.receiveTransportAck(contactId, a);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveTransportUpdate() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setRemoteProperties(txn, contactId, transportId,
					transportProperties, 1);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		TransportUpdate u = new TransportUpdate(transportId,
				transportProperties, 1);
		db.receiveTransportUpdate(contactId, u);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddingGroupMessageCallsListeners() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(true));
			oneOf(database).setReadFlag(txn, messageId, true);
			oneOf(database).getContactIds(txn);
			will(returnValue(Arrays.asList(contactId)));
			oneOf(database).addStatus(txn, contactId, messageId, false);
			oneOf(database).getRating(txn, authorId);
			will(returnValue(UNRATED));
			oneOf(database).getNumberOfSendableChildren(txn, messageId);
			will(returnValue(0));
			oneOf(database).setSendability(txn, messageId, 0);
			oneOf(database).commitTransaction(txn);
			// The message was added, so the listener should be called
			oneOf(listener).eventOccurred(with(any(
					GroupMessageAddedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// addLocalPrivateMessage(privateMessage, contactId)
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(true));
			oneOf(database).setReadFlag(txn, messageId, true);
			oneOf(database).addStatus(txn, contactId, messageId, false);
			// The message was added, so the listener should be called
			oneOf(listener).eventOccurred(with(any(
					PrivateMessageAddedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			// addLocalGroupMessage(message)
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).addGroupMessage(txn, message);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
			// The message was not added, so the listener should not be called
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// addLocalPrivateMessage(privateMessage, contactId)
			oneOf(database).addPrivateMessage(txn, privateMessage, contactId);
			will(returnValue(false));
			// The message was not added, so the listener should not be called
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
		db.addLocalPrivateMessage(privateMessage, contactId);

		context.assertIsSatisfied();
	}

	@Test
	public void testChangingLocalTransportPropertiesCallsListeners()
			throws Exception {
		final TransportProperties properties =
				new TransportProperties(Collections.singletonMap("bar", "baz"));
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).getLocalProperties(txn, transportId);
			will(returnValue(new TransportProperties()));
			oneOf(database).mergeLocalProperties(txn, transportId, properties);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.mergeLocalProperties(transportId, properties);

		context.assertIsSatisfied();
	}

	@Test
	public void testNotChangingLocalTransportPropertiesDoesNotCallListeners()
			throws Exception {
		final TransportProperties properties =
				new TransportProperties(Collections.singletonMap("bar", "baz"));
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).getLocalProperties(txn, transportId);
			will(returnValue(properties));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).commitTransaction(txn);
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// setSeen(contactId, Arrays.asList(messageId))
			oneOf(database).setStatusSeenIfVisible(txn, contactId, messageId);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.setSeen(contactId, Arrays.asList(messageId));

		context.assertIsSatisfied();
	}

	@Test
	public void testChangingVisibilityCallsListeners() throws Exception {
		final ContactId contactId1 = new ContactId(123);
		final Collection<ContactId> both = Arrays.asList(contactId, contactId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(both));
			oneOf(database).getContactIds(txn);
			will(returnValue(both));
			oneOf(database).removeVisibility(txn, contactId1, groupId);
			oneOf(database).commitTransaction(txn);
			oneOf(listener).eventOccurred(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
		db.setVisibility(groupId, Arrays.asList(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testNotChangingVisibilityDoesNotCallListeners()
			throws Exception {
		final ContactId contactId1 = new ContactId(234);
		final Collection<ContactId> both = Arrays.asList(contactId, contactId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final DatabaseListener listener = context.mock(DatabaseListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsSubscription(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(both));
			oneOf(database).getContactIds(txn);
			will(returnValue(both));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

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
		context.checking(new Expectations() {{
			// addSecrets()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).addSecrets(txn, Arrays.asList(temporarySecret));
			oneOf(database).commitTransaction(txn);
			// getSecrets()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).getSecrets(txn);
			will(returnValue(Arrays.asList(temporarySecret)));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addSecrets(Arrays.asList(temporarySecret));
		assertEquals(Arrays.asList(temporarySecret), db.getSecrets());

		context.assertIsSatisfied();
	}
}
