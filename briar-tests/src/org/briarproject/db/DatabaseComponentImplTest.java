package org.briarproject.db;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.Settings;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.MessageExistsException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchLocalAuthorException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.db.NoSuchTransportException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.LocalAuthorAddedEvent;
import org.briarproject.api.event.LocalAuthorRemovedEvent;
import org.briarproject.api.event.LocalSubscriptionsUpdatedEvent;
import org.briarproject.api.event.LocalTransportsUpdatedEvent;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageRequestedEvent;
import org.briarproject.api.event.MessageToAckEvent;
import org.briarproject.api.event.MessageToRequestEvent;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.event.MessagesAckedEvent;
import org.briarproject.api.event.MessagesSentEvent;
import org.briarproject.api.event.SubscriptionAddedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SubscriptionAck;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.TransportAck;
import org.briarproject.api.sync.TransportUpdate;
import org.briarproject.api.transport.IncomingKeys;
import org.briarproject.api.transport.OutgoingKeys;
import org.briarproject.api.transport.TransportKeys;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;
import static org.briarproject.db.DatabaseConstants.MAX_OFFERED_MESSAGES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class DatabaseComponentImplTest extends BriarTestCase {

	private final Object txn = new Object();
	private final ClientId clientId;
	private final GroupId groupId;
	private final Group group;
	private final AuthorId authorId;
	private final Author author;
	private final AuthorId localAuthorId;
	private final LocalAuthor localAuthor;
	private final MessageId messageId, messageId1;
	private final int size;
	private final byte[] raw;
	private final Message message;
	private final Metadata metadata;
	private final TransportId transportId;
	private final TransportProperties transportProperties;
	private final int maxLatency;
	private final ContactId contactId;
	private final Contact contact;

	public DatabaseComponentImplTest() {
		clientId = new ClientId(TestUtils.getRandomId());
		groupId = new GroupId(TestUtils.getRandomId());
		ClientId clientId = new ClientId(TestUtils.getRandomId());
		byte[] descriptor = new byte[MAX_GROUP_DESCRIPTOR_LENGTH];
		group = new Group(groupId, clientId, descriptor);
		authorId = new AuthorId(TestUtils.getRandomId());
		author = new Author(authorId, "Alice", new byte[MAX_PUBLIC_KEY_LENGTH]);
		localAuthorId = new AuthorId(TestUtils.getRandomId());
		localAuthor = new LocalAuthor(localAuthorId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100], 1234);
		messageId = new MessageId(TestUtils.getRandomId());
		messageId1 = new MessageId(TestUtils.getRandomId());
		long timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		message = new Message(messageId, groupId, timestamp, raw);
		metadata = new Metadata();
		metadata.put("foo", new byte[] {'b', 'a', 'r'});
		transportId = new TransportId("id");
		transportProperties = new TransportProperties(Collections.singletonMap(
				"bar", "baz"));
		maxLatency = Integer.MAX_VALUE;
		contactId = new ContactId(234);
		contact = new Contact(contactId, author, localAuthorId);
	}

	private <T> DatabaseComponent createDatabaseComponent(Database<T> database,
			EventBus eventBus, ShutdownManager shutdown) {
		return new DatabaseComponentImpl<T>(database, eventBus, shutdown);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSimpleCalls() throws Exception {
		final int shutdownHandle = 12345;
		Mockery context = new Mockery();
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			exactly(10).of(database).startTransaction();
			will(returnValue(txn));
			exactly(10).of(database).commitTransaction(txn);
			// open()
			oneOf(database).open();
			will(returnValue(false));
			oneOf(shutdown).addShutdownHook(with(any(Runnable.class)));
			will(returnValue(shutdownHandle));
			// addLocalAuthor()
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(false));
			oneOf(database).addLocalAuthor(txn, localAuthor);
			oneOf(eventBus).broadcast(with(any(LocalAuthorAddedEvent.class)));
			// addContact()
			oneOf(database).containsContact(txn, authorId);
			will(returnValue(false));
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(true));
			oneOf(database).addContact(txn, author, localAuthorId);
			will(returnValue(contactId));
			oneOf(eventBus).broadcast(with(any(ContactAddedEvent.class)));
			// getContacts()
			oneOf(database).getContacts(txn);
			will(returnValue(Collections.singletonList(contact)));
			// getRemoteProperties()
			oneOf(database).getRemoteProperties(txn, transportId);
			will(returnValue(Collections.emptyMap()));
			// addGroup()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(false));
			oneOf(database).addGroup(txn, group);
			will(returnValue(true));
			oneOf(eventBus).broadcast(with(any(SubscriptionAddedEvent.class)));
			// addGroup() again
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			// getGroups()
			oneOf(database).getGroups(txn, clientId);
			will(returnValue(Collections.singletonList(group)));
			// removeGroup()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Collections.emptyList()));
			oneOf(database).removeGroup(txn, groupId);
			oneOf(eventBus).broadcast(with(any(
					SubscriptionRemovedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
			// removeContact()
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).removeContact(txn, contactId);
			oneOf(eventBus).broadcast(with(any(ContactRemovedEvent.class)));
			// removeLocalAuthor()
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(true));
			oneOf(database).getContacts(txn, localAuthorId);
			will(returnValue(Collections.emptyList()));
			oneOf(database).removeLocalAuthor(txn, localAuthorId);
			oneOf(eventBus).broadcast(with(any(LocalAuthorRemovedEvent.class)));
			// close()
			oneOf(shutdown).removeShutdownHook(shutdownHandle);
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		assertFalse(db.open());
		db.addLocalAuthor(localAuthor);
		assertEquals(contactId, db.addContact(author, localAuthorId));
		assertEquals(Collections.singletonList(contact), db.getContacts());
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(transportId));
		db.addGroup(group); // First time - listeners called
		db.addGroup(group); // Second time - not called
		assertEquals(Collections.singletonList(group), db.getGroups(clientId));
		db.removeGroup(group);
		db.removeContact(contactId);
		db.removeLocalAuthor(localAuthorId);
		db.close();

		context.assertIsSatisfied();
	}

	@Test
	public void testDuplicateLocalMessagesAreNotStored() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		try {
			db.addLocalMessage(message, clientId, metadata);
			fail();
		} catch (MessageExistsException expected) {
			// Expected
		}

		context.assertIsSatisfied();
	}

	@Test
	public void testLocalMessagesAreNotStoredUnlessSubscribed()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(false));
			oneOf(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		try {
			db.addLocalMessage(message, clientId, metadata);
			fail();
		} catch (NoSuchSubscriptionException expected) {
			// Expected
		}

		context.assertIsSatisfied();
	}

	@Test
	public void testAddLocalMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message, true);
			oneOf(database).mergeMessageMetadata(txn, messageId, metadata);
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(database).getContactIds(txn);
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(database).removeOfferedMessage(txn, contactId, messageId);
			will(returnValue(false));
			oneOf(database).addStatus(txn, contactId, messageId, false, false);
			oneOf(database).commitTransaction(txn);
			// The message was added, so the listeners should be called
			oneOf(eventBus).broadcast(with(any(MessageAddedEvent.class)));
			oneOf(eventBus).broadcast(with(any(MessageValidatedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.addLocalMessage(message, clientId, metadata);

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfContactIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			// Check whether the contact is in the DB (which it's not)
			exactly(21).of(database).startTransaction();
			will(returnValue(txn));
			exactly(21).of(database).containsContact(txn, contactId);
			will(returnValue(false));
			exactly(21).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		try {
			db.addTransportKeys(contactId, createTransportKeys());
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.generateAck(contactId, 123);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.generateBatch(contactId, 123, 456);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.generateOffer(contactId, 123, 456);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.generateSubscriptionAck(contactId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.generateSubscriptionUpdate(contactId, 123);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.generateTransportAcks(contactId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.generateTransportUpdates(contactId, 123);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.getContact(contactId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.getMessageStatus(contactId, groupId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.getMessageStatus(contactId, messageId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.incrementStreamCounter(contactId, transportId, 0);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			Ack a = new Ack(Collections.singletonList(messageId));
			db.receiveAck(contactId, a);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.receiveMessage(contactId, message);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			Offer o = new Offer(Collections.singletonList(messageId));
			db.receiveOffer(contactId, o);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			SubscriptionAck a = new SubscriptionAck(0);
			db.receiveSubscriptionAck(contactId, a);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			SubscriptionUpdate u = new SubscriptionUpdate(
					Collections.<Group>emptyList(), 1);
			db.receiveSubscriptionUpdate(contactId, u);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			TransportAck a = new TransportAck(transportId, 0);
			db.receiveTransportAck(contactId, a);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			TransportUpdate u = new TransportUpdate(transportId,
					transportProperties, 1);
			db.receiveTransportUpdate(contactId, u);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.removeContact(contactId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.setReorderingWindow(contactId, transportId, 0, 0, new byte[4]);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfLocalAuthorIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		try {
			db.addContact(author, localAuthorId);
			fail();
		} catch (NoSuchLocalAuthorException expected) {
			// Expected
		}

		try {
			db.getLocalAuthor(localAuthorId);
			fail();
		} catch (NoSuchLocalAuthorException expected) {
			// Expected
		}

		try {
			db.removeLocalAuthor(localAuthorId);
			fail();
		} catch (NoSuchLocalAuthorException expected) {
			// Expected
		}

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfSubscriptionIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			// Check whether the subscription is in the DB (which it's not)
			exactly(5).of(database).startTransaction();
			will(returnValue(txn));
			exactly(5).of(database).containsGroup(txn, groupId);
			will(returnValue(false));
			exactly(5).of(database).abortTransaction(txn);
			// This is needed for getMessageStatus() to proceed
			exactly(1).of(database).containsContact(txn, contactId);
			will(returnValue(true));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		try {
			db.getGroup(groupId);
			fail();
		} catch (NoSuchSubscriptionException expected) {
			// Expected
		}

		try {
			db.getMessageStatus(contactId, groupId);
			fail();
		} catch (NoSuchSubscriptionException expected) {
			// Expected
		}

		try {
			db.getVisibility(groupId);
			fail();
		} catch (NoSuchSubscriptionException expected) {
			// Expected
		}

		try {
			db.removeGroup(group);
			fail();
		} catch (NoSuchSubscriptionException expected) {
			// Expected
		}

		try {
			db.setVisibility(groupId, Collections.<ContactId>emptyList());
			fail();
		} catch (NoSuchSubscriptionException expected) {
			// Expected
		}

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfMessageIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			// Check whether the message is in the DB (which it's not)
			exactly(4).of(database).startTransaction();
			will(returnValue(txn));
			exactly(4).of(database).containsMessage(txn, messageId);
			will(returnValue(false));
			exactly(4).of(database).abortTransaction(txn);
			// This is needed for getMessageStatus() to proceed
			exactly(1).of(database).containsContact(txn, contactId);
			will(returnValue(true));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		try {
			db.getRawMessage(messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.getMessageMetadata(messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.getMessageStatus(contactId, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.mergeMessageMetadata(messageId, metadata);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		context.assertIsSatisfied();
	}

	@Test
	public void testVariousMethodsThrowExceptionIfTransportIsMissing()
			throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			// addLocalAuthor()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsLocalAuthor(txn, localAuthorId);
			will(returnValue(false));
			oneOf(database).addLocalAuthor(txn, localAuthor);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(LocalAuthorAddedEvent.class)));
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
			oneOf(eventBus).broadcast(with(any(ContactAddedEvent.class)));
			// Check whether the transport is in the DB (which it's not)
			exactly(6).of(database).startTransaction();
			will(returnValue(txn));
			exactly(2).of(database).containsContact(txn, contactId);
			will(returnValue(true));
			exactly(6).of(database).containsTransport(txn, transportId);
			will(returnValue(false));
			exactly(6).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.addLocalAuthor(localAuthor);
		assertEquals(contactId, db.addContact(author, localAuthorId));

		try {
			db.getLocalProperties(transportId);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.getTransportKeys(transportId);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.mergeLocalProperties(transportId, new TransportProperties());
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.incrementStreamCounter(contactId, transportId, 0);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.removeTransport(transportId);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.setReorderingWindow(contactId, transportId, 0, 0, new byte[4]);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateAck() throws Exception {
		final Collection<MessageId> messagesToAck = Arrays.asList(messageId,
				messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
					maxLatency);
			oneOf(database).getRawMessage(txn, messageId1);
			will(returnValue(raw1));
			oneOf(database).updateExpiryTime(txn, contactId, messageId1,
					maxLatency);
			oneOf(database).lowerRequestedFlag(txn, contactId, ids);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(MessagesSentEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		assertEquals(messages, db.generateBatch(contactId, size * 2,
				maxLatency));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateOffer() throws Exception {
		final MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		final Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getMessagesToOffer(txn, contactId, 123);
			will(returnValue(ids));
			oneOf(database).updateExpiryTime(txn, contactId, messageId,
					maxLatency);
			oneOf(database).updateExpiryTime(txn, contactId, messageId1,
					maxLatency);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Offer o = db.generateOffer(contactId, 123, maxLatency);
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
					maxLatency);
			oneOf(database).getRawMessage(txn, messageId1);
			will(returnValue(raw1));
			oneOf(database).updateExpiryTime(txn, contactId, messageId1,
					maxLatency);
			oneOf(database).lowerRequestedFlag(txn, contactId, ids);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(MessagesSentEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		assertEquals(messages, db.generateRequestedBatch(contactId, size * 2,
				maxLatency));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateSubscriptionUpdateNoUpdateDue() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getSubscriptionUpdate(txn, contactId, maxLatency);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		assertNull(db.generateSubscriptionUpdate(contactId, maxLatency));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateSubscriptionUpdate() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getSubscriptionUpdate(txn, contactId, maxLatency);
			will(returnValue(new SubscriptionUpdate(
					Collections.singletonList(group), 1)));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		SubscriptionUpdate u = db.generateSubscriptionUpdate(contactId,
				maxLatency);
		assertEquals(Collections.singletonList(group), u.getGroups());
		assertEquals(1, u.getVersion());

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateTransportUpdatesNoUpdatesDue() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransportUpdates(txn, contactId, maxLatency);
			will(returnValue(null));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		assertNull(db.generateTransportUpdates(contactId, maxLatency));

		context.assertIsSatisfied();
	}

	@Test
	public void testGenerateTransportUpdates() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getTransportUpdates(txn, contactId, maxLatency);
			will(returnValue(Collections.singletonList(new TransportUpdate(
					transportId, transportProperties, 1))));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Collection<TransportUpdate> updates =
				db.generateTransportUpdates(contactId, maxLatency);
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsVisibleMessage(txn, contactId, messageId);
			will(returnValue(true));
			oneOf(database).raiseSeenFlag(txn, contactId, messageId);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(MessagesAckedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.receiveAck(contactId, new Ack(Collections.singletonList(messageId)));

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).containsVisibleGroup(txn, contactId, groupId);
			will(returnValue(true));
			oneOf(database).addMessage(txn, message, false);
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(database).getContactIds(txn);
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(database).removeOfferedMessage(txn, contactId, messageId);
			will(returnValue(false));
			oneOf(database).addStatus(txn, contactId, messageId, false, true);
			oneOf(database).raiseAckFlag(txn, contactId, messageId);
			oneOf(database).commitTransaction(txn);
			// The message was received and added
			oneOf(eventBus).broadcast(with(any(MessageToAckEvent.class)));
			oneOf(eventBus).broadcast(with(any(MessageAddedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.receiveMessage(contactId, message);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveDuplicateMessage() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
			oneOf(eventBus).broadcast(with(any(MessageToAckEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.receiveMessage(contactId, message);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveMessageWithoutVisibleGroup() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
			oneOf(eventBus).broadcast(with(any(MessageToAckEvent.class)));
			oneOf(eventBus).broadcast(with(any(MessageToRequestEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
			oneOf(eventBus).broadcast(with(any(MessageRequestedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.receiveRequest(contactId, new Request(Collections.singletonList(
				messageId)));

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveSubscriptionAck() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setSubscriptionUpdateAcked(txn, contactId, 1);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setGroups(txn, contactId,
					Collections.singletonList(group), 1);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		SubscriptionUpdate u = new SubscriptionUpdate(
				Collections.singletonList(group), 1);
		db.receiveSubscriptionUpdate(contactId, u);

		context.assertIsSatisfied();
	}

	@Test
	public void testReceiveTransportAck() throws Exception {
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).setRemoteProperties(txn, contactId, transportId,
					transportProperties, 1);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).getLocalProperties(txn, transportId);
			will(returnValue(new TransportProperties()));
			oneOf(database).mergeLocalProperties(txn, transportId, properties);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(
					LocalTransportsUpdatedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).getLocalProperties(txn, transportId);
			will(returnValue(properties));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
			oneOf(eventBus).broadcast(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.setVisibility(groupId, Collections.singletonList(contactId));

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

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
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
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
			oneOf(eventBus).broadcast(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
			// setVisibleToAll()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).setVisibleToAll(txn, groupId, true);
			oneOf(database).getVisibility(txn, groupId);
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(database).getContactIds(txn);
			will(returnValue(both));
			oneOf(database).addVisibility(txn, contactId1, groupId);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(
					LocalSubscriptionsUpdatedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.setVisibility(groupId, Collections.singletonList(contactId));
		db.setVisibleToAll(groupId, true);

		context.assertIsSatisfied();
	}

	@Test
	public void testTransportKeys() throws Exception {
		final TransportKeys keys = createTransportKeys();
		Mockery context = new Mockery();
		@SuppressWarnings("unchecked")
		final Database<Object> database = context.mock(Database.class);
		final ShutdownManager shutdown = context.mock(ShutdownManager.class);
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			// updateTransportKeys()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).updateTransportKeys(txn,
					Collections.singletonMap(contactId, keys));
			oneOf(database).commitTransaction(txn);
			// getTransportKeys()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).getTransportKeys(txn, transportId);
			will(returnValue(Collections.singletonMap(contactId, keys)));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		db.updateTransportKeys(Collections.singletonMap(contactId, keys));
		assertEquals(Collections.singletonMap(contactId, keys),
				db.getTransportKeys(transportId));

		context.assertIsSatisfied();
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
}
