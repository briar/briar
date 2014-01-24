package org.briarproject.db;

import static org.briarproject.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.GROUP_SALT_LENGTH;
import static org.briarproject.db.DatabaseConstants.MAX_OFFERED_MESSAGES;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.briarproject.BriarTestCase;
import org.briarproject.TestMessage;
import org.briarproject.TestUtils;
import org.briarproject.api.Author;
import org.briarproject.api.AuthorId;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchLocalAuthorException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.db.NoSuchTransportException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.LocalAuthorAddedEvent;
import org.briarproject.api.event.LocalAuthorRemovedEvent;
import org.briarproject.api.event.LocalSubscriptionsUpdatedEvent;
import org.briarproject.api.event.LocalTransportsUpdatedEvent;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageToAckEvent;
import org.briarproject.api.event.MessageToRequestEvent;
import org.briarproject.api.event.SubscriptionAddedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.Offer;
import org.briarproject.api.messaging.Request;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.transport.Endpoint;
import org.briarproject.api.transport.TemporarySecret;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public abstract class DatabaseComponentTest extends BriarTestCase {

	protected final Object txn = new Object();
	protected final GroupId groupId;
	protected final Group group;
	protected final AuthorId authorId;
	protected final Author author;
	protected final AuthorId localAuthorId;
	protected final LocalAuthor localAuthor;
	protected final MessageId messageId, messageId1;
	protected final String contentType, subject;
	protected final long timestamp;
	protected final int size;
	protected final byte[] raw;
	protected final Message message, message1;
	protected final TransportId transportId;
	protected final TransportProperties transportProperties;
	protected final ContactId contactId;
	protected final Contact contact;
	protected final Endpoint endpoint;
	protected final TemporarySecret temporarySecret;

	public DatabaseComponentTest() {
		groupId = new GroupId(TestUtils.getRandomId());
		group = new Group(groupId, "Group", new byte[GROUP_SALT_LENGTH]);
		authorId = new AuthorId(TestUtils.getRandomId());
		author = new Author(authorId, "Alice", new byte[MAX_PUBLIC_KEY_LENGTH]);
		localAuthorId = new AuthorId(TestUtils.getRandomId());
		localAuthor = new LocalAuthor(localAuthorId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100]);
		messageId = new MessageId(TestUtils.getRandomId());
		messageId1 = new MessageId(TestUtils.getRandomId());
		contentType = "text/plain";
		subject = "Foo";
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		message = new TestMessage(messageId, null, group, author, contentType,
				subject, timestamp, raw);
		message1 = new TestMessage(messageId1, messageId, group, null,
				contentType, subject, timestamp, raw);
		transportId = new TransportId("id");
		transportProperties = new TransportProperties(Collections.singletonMap(
				"bar", "baz"));
		contactId = new ContactId(234);
		contact = new Contact(contactId, author, localAuthorId);
		endpoint = new Endpoint(contactId, transportId, 123, true);
		temporarySecret = new TemporarySecret(contactId, transportId, 123,
				false, 234, new byte[32], 345, 456, new byte[4]);
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
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			exactly(11).of(database).startTransaction();
			will(returnValue(txn));
			exactly(11).of(database).commitTransaction(txn);
			// open()
			oneOf(database).open();
			will(returnValue(false));
			oneOf(cleaner).startCleaning(
					with(any(DatabaseCleaner.Callback.class)),
					with(any(long.class)));
			oneOf(shutdown).addShutdownHook(with(any(Runnable.class)));
			will(returnValue(shutdownHandle));
			// addLocalAuthor()
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(false));
			oneOf(database).addLocalAuthor(txn, localAuthor);
			oneOf(listener).eventOccurred(with(any(
					LocalAuthorAddedEvent.class)));
			// addContact()
			oneOf(database).containsContact(txn, authorId);
			will(returnValue(false));
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(true));
			oneOf(database).addContact(txn, author, localAuthorId);
			will(returnValue(contactId));
			oneOf(listener).eventOccurred(with(any(ContactAddedEvent.class)));
			// getContacts()
			oneOf(database).getContacts(txn);
			will(returnValue(Arrays.asList(contact)));
			// getRemoteProperties()
			oneOf(database).getRemoteProperties(txn, transportId);
			will(returnValue(Collections.emptyMap()));
			// addGroup()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(false));
			oneOf(database).addGroup(txn, group);
			will(returnValue(true));
			oneOf(listener).eventOccurred(with(any(
					SubscriptionAddedEvent.class)));
			// addGroup() again
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			// getMessageHeaders()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getMessageHeaders(txn, groupId);
			will(returnValue(Collections.emptyList()));
			// getGroups()
			oneOf(database).getGroups(txn);
			will(returnValue(Arrays.asList(groupId)));
			// removeGroup()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Collections.emptyList()));
			oneOf(database).removeGroup(txn, groupId);
			oneOf(listener).eventOccurred(with(any(
					SubscriptionRemovedEvent.class)));
			oneOf(listener).eventOccurred(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
			// removeContact()
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getInboxGroupId(txn, contactId);
			will(returnValue(null));
			oneOf(database).removeContact(txn, contactId);
			oneOf(listener).eventOccurred(with(any(ContactRemovedEvent.class)));
			// removeLocalAuthor()
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(true));
			oneOf(database).getContacts(txn, localAuthorId);
			will(returnValue(Collections.emptyList()));
			oneOf(database).removeLocalAuthor(txn, localAuthorId);
			oneOf(listener).eventOccurred(with(any(
					LocalAuthorRemovedEvent.class)));
			// close()
			oneOf(shutdown).removeShutdownHook(shutdownHandle);
			oneOf(cleaner).stopCleaning();
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		assertFalse(db.open());
		db.addListener(listener);
		db.addLocalAuthor(localAuthor);
		assertEquals(contactId, db.addContact(author, localAuthorId));
		assertEquals(Arrays.asList(contact), db.getContacts());
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(transportId));
		db.addGroup(group); // First time - listeners called
		db.addGroup(group); // Second time - not called
		assertEquals(Collections.emptyList(), db.getMessageHeaders(groupId));
		assertEquals(Arrays.asList(groupId), db.getGroups());
		db.removeGroup(group);
		db.removeContact(contactId);
		db.removeLocalAuthor(localAuthorId);
		db.removeListener(listener);
		db.close();

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicateLocalMessagesAreNotStored() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addLocalMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testLocalMessagesAreNotStoredUnlessSubscribed()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addLocalMessage(message);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocalMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message, false);
			oneOf(database).setReadFlag(txn, messageId, true);
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Arrays.asList(contactId)));
			oneOf(database).getContactIds(txn);
			will(returnValue(Arrays.asList(contactId)));
			oneOf(database).removeOfferedMessage(txn, contactId, messageId);
			will(returnValue(false));
			oneOf(database).addStatus(txn, contactId, messageId, false, false);
			oneOf(database).commitTransaction(txn);
			// The message was added, so the listener should be called
			oneOf(listener).eventOccurred(with(any(
					MessageAddedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
		db.addLocalMessage(message);

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
			exactly(25).of(database).startTransaction();
			will(returnValue(txn));
			exactly(25).of(database).containsContact(txn, contactId);
			will(returnValue(false));
			exactly(25).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		try {
			db.addEndpoint(endpoint);
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
			db.generateOffer(contactId, 123, 456);
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
			db.getInboxGroupId(contactId);
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
			db.setInboxGroup(contactId, group);
			fail();
		} catch(NoSuchContactException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfLocalAuthorIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			// Check whether the pseudonym is in the DB (which it's not)
			exactly(3).of(database).startTransaction();
			will(returnValue(txn));
			exactly(3).of(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(false));
			exactly(3).of(database).abortTransaction(txn);
			// This is needed for addContact() to proceed
			exactly(1).of(database).containsContact(txn, authorId);
			will(returnValue(false));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		try {
			db.addContact(author, localAuthorId);
			fail();
		} catch(NoSuchLocalAuthorException expected) {}

		try {
			db.getLocalAuthor(localAuthorId);
			fail();
		} catch(NoSuchLocalAuthorException expected) {}

		try {
			db.removeLocalAuthor(localAuthorId);
			fail();
		} catch(NoSuchLocalAuthorException expected) {}

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
			exactly(5).of(database).containsGroup(txn, groupId);
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
			db.removeGroup(group);
			fail();
		} catch(NoSuchSubscriptionException expected) {}

		try {
			db.setVisibility(groupId, Collections.<ContactId>emptyList());
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
			// addLocalAuthor()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(false));
			oneOf(database).addLocalAuthor(txn, localAuthor);
			oneOf(database).commitTransaction(txn);
			// addContact()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, authorId);
			will(returnValue(false));
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(true));
			oneOf(database).addContact(txn, author, localAuthorId);
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
		db.addLocalAuthor(localAuthor);
		assertEquals(contactId, db.addContact(author, localAuthorId));

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
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getMessagesToAck(txn, contactId, 123);
			will(returnValue(messagesToAck));
			oneOf(database).lowerAckFlag(txn, contactId, messagesToAck);
			oneOf(database).commitTransaction(txn);
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
		final Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		final Collection<byte[]> messages = Arrays.asList(raw, raw1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getMessagesToSend(txn, contactId, size * 2);
			will(returnValue(ids));
			oneOf(database).getRawMessage(txn, messageId);
			will(returnValue(raw));
			oneOf(database).updateExpiryTime(txn, contactId, messageId,
					Long.MAX_VALUE);
			oneOf(database).getRawMessage(txn, messageId1);
			will(returnValue(raw1));
			oneOf(database).updateExpiryTime(txn, contactId, messageId1,
					Long.MAX_VALUE);
			oneOf(database).lowerRequestedFlag(txn, contactId, ids);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		assertEquals(messages, db.generateBatch(contactId, size * 2,
				Long.MAX_VALUE));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateOffer() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getMessagesToOffer(txn, contactId, 123);
			will(returnValue(ids));
			oneOf(database).updateExpiryTime(txn, contactId, messageId,
					Long.MAX_VALUE);
			oneOf(database).updateExpiryTime(txn, contactId, messageId1,
					Long.MAX_VALUE);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		Offer o = db.generateOffer(contactId, 123, Long.MAX_VALUE);
		assertEquals(ids, o.getMessageIds());

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateRequest() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getMessagesToRequest(txn, contactId, 123);
			will(returnValue(ids));
			oneOf(database).removeOfferedMessages(txn, contactId, ids);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		Request r = db.generateRequest(contactId, 123);
		assertEquals(ids, r.getMessageIds());

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateRequestedBatch() throws Exception {
		final byte[] raw1 = new byte[size];
		final Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		final Collection<byte[]> messages = Arrays.asList(raw, raw1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getRequestedMessagesToSend(txn, contactId,
					size * 2);
			will(returnValue(ids));
			oneOf(database).getRawMessage(txn, messageId);
			will(returnValue(raw));
			oneOf(database).updateExpiryTime(txn, contactId, messageId,
					Long.MAX_VALUE);
			oneOf(database).getRawMessage(txn, messageId1);
			will(returnValue(raw1));
			oneOf(database).updateExpiryTime(txn, contactId, messageId1,
					Long.MAX_VALUE);
			oneOf(database).lowerRequestedFlag(txn, contactId, ids);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		assertEquals(messages, db.generateRequestedBatch(contactId, size * 2,
				Long.MAX_VALUE));

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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getRetentionUpdate(txn, contactId, Long.MAX_VALUE);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getRetentionUpdate(txn, contactId, Long.MAX_VALUE);
			will(returnValue(new RetentionUpdate(0, 1)));
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getSubscriptionUpdate(txn, contactId,
					Long.MAX_VALUE);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getSubscriptionUpdate(txn, contactId,
					Long.MAX_VALUE);
			will(returnValue(new SubscriptionUpdate(Arrays.asList(group), 1)));
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransportUpdates(txn, contactId, Long.MAX_VALUE);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransportUpdates(txn, contactId, Long.MAX_VALUE);
			will(returnValue(Arrays.asList(new TransportUpdate(transportId,
					transportProperties, 1))));
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsVisibleMessage(txn, contactId, messageId);
			will(returnValue(true));
			oneOf(database).raiseSeenFlag(txn, contactId, messageId);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveAck(contactId, new Ack(Arrays.asList(messageId)));

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).containsVisibleGroup(txn, contactId, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message, true);
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Arrays.asList(contactId)));
			oneOf(database).getContactIds(txn);
			will(returnValue(Arrays.asList(contactId)));
			oneOf(database).removeOfferedMessage(txn, contactId, messageId);
			will(returnValue(false));
			oneOf(database).addStatus(txn, contactId, messageId, false, true);
			oneOf(database).raiseAckFlag(txn, contactId, messageId);
			oneOf(database).commitTransaction(txn);
			// The message was received and added
			oneOf(listener).eventOccurred(with(any(
					MessageToAckEvent.class)));
			oneOf(listener).eventOccurred(with(any(MessageAddedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
		db.receiveMessage(contactId, message);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveDuplicateMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).containsVisibleGroup(txn, contactId, groupId);
			will(returnValue(true));
			// The message wasn't stored but it must still be acked
			oneOf(database).raiseAckFlag(txn, contactId, messageId);
			oneOf(database).commitTransaction(txn);
			// The message was received but not added
			oneOf(listener).eventOccurred(with(any(
					MessageToAckEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
		db.receiveMessage(contactId, message);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveMessageWithoutVisibleGroup() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).containsVisibleGroup(txn, contactId, groupId);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
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
		final MessageId messageId3 = new MessageId(TestUtils.getRandomId());
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			// There's room for two more offered messages
			oneOf(database).countOfferedMessages(txn, contactId);
			will(returnValue(MAX_OFFERED_MESSAGES - 2));
			// The first message isn't visible - request it
			oneOf(database).containsVisibleMessage(txn, contactId, messageId);
			will(returnValue(false));
			oneOf(database).addOfferedMessage(txn, contactId, messageId);
			// The second message is visible - ack it
			oneOf(database).containsVisibleMessage(txn, contactId, messageId1);
			will(returnValue(true));
			oneOf(database).raiseSeenFlag(txn, contactId, messageId1);
			oneOf(database).raiseAckFlag(txn, contactId, messageId1);
			// The third message isn't visible - request it
			oneOf(database).containsVisibleMessage(txn, contactId, messageId2);
			will(returnValue(false));
			oneOf(database).addOfferedMessage(txn, contactId, messageId2);
			// The fourth message isn't visible, but there's no room to store it
			oneOf(database).containsVisibleMessage(txn, contactId, messageId3);
			will(returnValue(false));
			oneOf(database).commitTransaction(txn);
			oneOf(listener).eventOccurred(with(any(MessageToAckEvent.class)));
			oneOf(listener).eventOccurred(with(any(
					MessageToRequestEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
		Offer o = new Offer(Arrays.asList(messageId, messageId1, messageId2,
				messageId3));
		db.receiveOffer(contactId, o);
		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveRequest() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsVisibleMessage(txn, contactId, messageId);
			will(returnValue(true));
			oneOf(database).raiseRequestedFlag(txn, contactId, messageId);
			oneOf(database).resetExpiryTime(txn, contactId, messageId);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.receiveRequest(contactId, new Request(Arrays.asList(messageId)));

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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setRetentionUpdateAcked(txn, contactId, 1);
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setSubscriptionUpdateAcked(txn, contactId, 1);
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setGroups(txn, contactId, Arrays.asList(group), 1);
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).setTransportUpdateAcked(txn, contactId,
					transportId, 1);
			oneOf(database).commitTransaction(txn);
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
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setRemoteProperties(txn, contactId, transportId,
					transportProperties, 1);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		TransportUpdate u = new TransportUpdate(transportId,
				transportProperties, 1);
		db.receiveTransportUpdate(contactId, u);

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
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).getLocalProperties(txn, transportId);
			will(returnValue(new TransportProperties()));
			oneOf(database).mergeLocalProperties(txn, transportId, properties);
			oneOf(database).commitTransaction(txn);
			oneOf(listener).eventOccurred(with(any(
					LocalTransportsUpdatedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
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
		final EventListener listener = context.mock(EventListener.class);
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
	public void testChangingVisibilityCallsListeners() throws Exception {
		final ContactId contactId1 = new ContactId(123);
		final Collection<ContactId> both = Arrays.asList(contactId, contactId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(both));
			oneOf(database).getContactIds(txn);
			will(returnValue(both));
			oneOf(database).removeVisibility(txn, contactId1, groupId);
			oneOf(database).setVisibleToAll(txn, groupId, false);
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
		final ContactId contactId1 = new ContactId(123);
		final Collection<ContactId> both = Arrays.asList(contactId, contactId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(both));
			oneOf(database).getContactIds(txn);
			will(returnValue(both));
			oneOf(database).setVisibleToAll(txn, groupId, false);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
		db.setVisibility(groupId, both);

		context.assertIsSatisfied();
	}

	@Test
	public void testSettingVisibleToAllAffectsCurrentContacts()
			throws Exception {
		final ContactId contactId1 = new ContactId(123);
		final Collection<ContactId> both = Arrays.asList(contactId, contactId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final DatabaseCleaner cleaner = context.mock(DatabaseCleaner.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventListener listener = context.mock(EventListener.class);
		context.checking(new Expectations() {{
			// setVisibility()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Collections.emptyList()));
			oneOf(database).getContactIds(txn);
			will(returnValue(both));
			oneOf(database).addVisibility(txn, contactId, groupId);
			oneOf(database).setVisibleToAll(txn, groupId, false);
			oneOf(database).commitTransaction(txn);
			oneOf(listener).eventOccurred(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
			// setVisibleToAll()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).setVisibleToAll(txn, groupId, true);
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Arrays.asList(contactId)));
			oneOf(database).getContactIds(txn);
			will(returnValue(both));
			oneOf(database).addVisibility(txn, contactId1, groupId);
			oneOf(database).commitTransaction(txn);
			oneOf(listener).eventOccurred(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, cleaner,
				shutdown);

		db.addListener(listener);
		db.setVisibility(groupId, Arrays.asList(contactId));
		db.setVisibleToAll(groupId, true);

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
