package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.contact.event.ContactStatusChangedEvent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.NoSuchGroupException;
import org.briarproject.bramble.api.db.NoSuchLocalAuthorException;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.db.NoSuchTransportException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.identity.event.LocalAuthorAddedEvent;
import org.briarproject.bramble.api.identity.event.LocalAuthorRemovedEvent;
import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.sync.Offer;
import org.briarproject.bramble.api.sync.Request;
import org.briarproject.bramble.api.sync.event.GroupAddedEvent;
import org.briarproject.bramble.api.sync.event.GroupRemovedEvent;
import org.briarproject.bramble.api.sync.event.GroupVisibilityUpdatedEvent;
import org.briarproject.bramble.api.sync.event.MessageAddedEvent;
import org.briarproject.bramble.api.sync.event.MessageRequestedEvent;
import org.briarproject.bramble.api.sync.event.MessageSharedEvent;
import org.briarproject.bramble.api.sync.event.MessageStateChangedEvent;
import org.briarproject.bramble.api.sync.event.MessageToAckEvent;
import org.briarproject.bramble.api.sync.event.MessageToRequestEvent;
import org.briarproject.bramble.api.sync.event.MessagesAckedEvent;
import org.briarproject.bramble.api.sync.event.MessagesSentEvent;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.KeySet;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.bramble.api.sync.ValidationManager.State.UNKNOWN;
import static org.briarproject.bramble.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;
import static org.briarproject.bramble.db.DatabaseConstants.MAX_OFFERED_MESSAGES;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseComponentImplTest extends BrambleMockTestCase {

	@SuppressWarnings("unchecked")
	private final Database<Object> database = context.mock(Database.class);
	private final ShutdownManager shutdown =
			context.mock(ShutdownManager.class);
	private final EventBus eventBus = context.mock(EventBus.class);

	private final Object txn = new Object();
	private final ClientId clientId;
	private final int majorVersion;
	private final GroupId groupId;
	private final Group group;
	private final Author author;
	private final LocalAuthor localAuthor;
	private final MessageId messageId, messageId1;
	private final int size;
	private final byte[] raw;
	private final Message message;
	private final Metadata metadata;
	private final TransportId transportId;
	private final int maxLatency;
	private final ContactId contactId;
	private final Contact contact;
	private final KeySetId keySetId;

	public DatabaseComponentImplTest() {
		clientId = getClientId();
		majorVersion = 123;
		group = getGroup(clientId, majorVersion);
		groupId = group.getId();
		author = getAuthor();
		localAuthor = getLocalAuthor();
		messageId = new MessageId(getRandomId());
		messageId1 = new MessageId(getRandomId());
		long timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		message = new Message(messageId, groupId, timestamp, raw);
		metadata = new Metadata();
		metadata.put("foo", new byte[] {'b', 'a', 'r'});
		transportId = getTransportId();
		maxLatency = Integer.MAX_VALUE;
		contactId = new ContactId(234);
		contact = new Contact(contactId, author, localAuthor.getId(),
				true, true);
		keySetId = new KeySetId(345);
	}

	private DatabaseComponent createDatabaseComponent(Database<Object> database,
			EventBus eventBus, ShutdownManager shutdown) {
		return new DatabaseComponentImpl<>(database, Object.class, eventBus,
				shutdown);
	}

	@Test
	public void testSimpleCalls() throws Exception {
		int shutdownHandle = 12345;
		context.checking(new Expectations() {{
			// open()
			oneOf(database).open(null);
			will(returnValue(false));
			oneOf(shutdown).addShutdownHook(with(any(Runnable.class)));
			will(returnValue(shutdownHandle));
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// registerLocalAuthor()
			oneOf(database).containsLocalAuthor(txn, localAuthor.getId());
			will(returnValue(false));
			oneOf(database).addLocalAuthor(txn, localAuthor);
			oneOf(eventBus).broadcast(with(any(LocalAuthorAddedEvent.class)));
			// addContact()
			oneOf(database).containsLocalAuthor(txn, localAuthor.getId());
			will(returnValue(true));
			oneOf(database).containsLocalAuthor(txn, author.getId());
			will(returnValue(false));
			oneOf(database).containsContact(txn, author.getId(),
					localAuthor.getId());
			will(returnValue(false));
			oneOf(database).addContact(txn, author, localAuthor.getId(),
					true, true);
			will(returnValue(contactId));
			oneOf(eventBus).broadcast(with(any(ContactAddedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					ContactStatusChangedEvent.class)));
			// getContacts()
			oneOf(database).getContacts(txn);
			will(returnValue(singletonList(contact)));
			// addGroup()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(false));
			oneOf(database).addGroup(txn, group);
			oneOf(eventBus).broadcast(with(any(GroupAddedEvent.class)));
			// addGroup() again
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			// getGroups()
			oneOf(database).getGroups(txn, clientId, majorVersion);
			will(returnValue(singletonList(group)));
			// removeGroup()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, groupId);
			will(returnValue(emptyMap()));
			oneOf(database).removeGroup(txn, groupId);
			oneOf(eventBus).broadcast(with(any(GroupRemovedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					GroupVisibilityUpdatedEvent.class)));
			// removeContact()
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).removeContact(txn, contactId);
			oneOf(eventBus).broadcast(with(any(ContactRemovedEvent.class)));
			// removeLocalAuthor()
			oneOf(database).containsLocalAuthor(txn, localAuthor.getId());
			will(returnValue(true));
			oneOf(database).removeLocalAuthor(txn, localAuthor.getId());
			oneOf(eventBus).broadcast(with(any(LocalAuthorRemovedEvent.class)));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
			// close()
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		assertFalse(db.open(null));
		Transaction transaction = db.startTransaction(false);
		try {
			db.addLocalAuthor(transaction, localAuthor);
			assertEquals(contactId, db.addContact(transaction, author,
					localAuthor.getId(), true, true));
			assertEquals(singletonList(contact),
					db.getContacts(transaction));
			db.addGroup(transaction, group); // First time - listeners called
			db.addGroup(transaction, group); // Second time - not called
			assertEquals(singletonList(group),
					db.getGroups(transaction, clientId, majorVersion));
			db.removeGroup(transaction, group);
			db.removeContact(transaction, contactId);
			db.removeLocalAuthor(transaction, localAuthor.getId());
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
		db.close();
	}

	@Test
	public void testLocalMessagesAreNotStoredUnlessGroupExists()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(false));
			oneOf(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.addLocalMessage(transaction, message, metadata, true);
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testAddLocalMessage() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).addMessage(txn, message, DELIVERED, true, null);
			oneOf(database).mergeMessageMetadata(txn, messageId, metadata);
			oneOf(database).commitTransaction(txn);
			// The message was added, so the listeners should be called
			oneOf(eventBus).broadcast(with(any(MessageAddedEvent.class)));
			oneOf(eventBus)
					.broadcast(with(any(MessageStateChangedEvent.class)));
			oneOf(eventBus).broadcast(with(any(MessageSharedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.addLocalMessage(transaction, message, metadata, true);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfContactIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the contact is in the DB (which it's not)
			exactly(16).of(database).startTransaction();
			will(returnValue(txn));
			exactly(16).of(database).containsContact(txn, contactId);
			will(returnValue(false));
			exactly(16).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.addTransportKeys(transaction, contactId, createTransportKeys());
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.generateAck(transaction, contactId, 123);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.generateBatch(transaction, contactId, 123, 456);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.generateOffer(transaction, contactId, 123, 456);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.generateRequest(transaction, contactId, 123);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getContact(transaction, contactId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getMessageStatus(transaction, contactId, groupId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getMessageStatus(transaction, contactId, messageId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getGroupVisibility(transaction, contactId, groupId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			Ack a = new Ack(singletonList(messageId));
			db.receiveAck(transaction, contactId, a);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.receiveMessage(transaction, contactId, message);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			Offer o = new Offer(singletonList(messageId));
			db.receiveOffer(transaction, contactId, o);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			Request r = new Request(singletonList(messageId));
			db.receiveRequest(transaction, contactId, r);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.removeContact(transaction, contactId);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.setContactActive(transaction, contactId, true);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.setGroupVisibility(transaction, contactId, groupId, SHARED);
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfLocalAuthorIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the pseudonym is in the DB (which it's not)
			exactly(3).of(database).startTransaction();
			will(returnValue(txn));
			exactly(3).of(database).containsLocalAuthor(txn,
					localAuthor.getId());
			will(returnValue(false));
			exactly(3).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.addContact(transaction, author, localAuthor.getId(), true, true);
			fail();
		} catch (NoSuchLocalAuthorException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getLocalAuthor(transaction, localAuthor.getId());
			fail();
		} catch (NoSuchLocalAuthorException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.removeLocalAuthor(transaction, localAuthor.getId());
			fail();
		} catch (NoSuchLocalAuthorException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfGroupIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the group is in the DB (which it's not)
			exactly(8).of(database).startTransaction();
			will(returnValue(txn));
			exactly(8).of(database).containsGroup(txn, groupId);
			will(returnValue(false));
			exactly(8).of(database).abortTransaction(txn);
			// This is needed for getMessageStatus() and setGroupVisibility()
			exactly(2).of(database).containsContact(txn, contactId);
			will(returnValue(true));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.getGroup(transaction, groupId);
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getGroupMetadata(transaction, groupId);
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getMessageMetadata(transaction, groupId);
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getMessageMetadata(transaction, groupId, new Metadata());
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getMessageStatus(transaction, contactId, groupId);
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.mergeGroupMetadata(transaction, groupId, metadata);
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.removeGroup(transaction, group);
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.setGroupVisibility(transaction, contactId, groupId, SHARED);
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfMessageIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the message is in the DB (which it's not)
			exactly(11).of(database).startTransaction();
			will(returnValue(txn));
			exactly(11).of(database).containsMessage(txn, messageId);
			will(returnValue(false));
			exactly(11).of(database).abortTransaction(txn);
			// This is needed for getMessageStatus() to proceed
			exactly(1).of(database).containsContact(txn, contactId);
			will(returnValue(true));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.deleteMessage(transaction, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.deleteMessageMetadata(transaction, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getRawMessage(transaction, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getMessageMetadata(transaction, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getMessageState(transaction, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getMessageStatus(transaction, contactId, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.mergeMessageMetadata(transaction, messageId, metadata);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.setMessageShared(transaction, message.getId());
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.setMessageState(transaction, messageId, DELIVERED);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(true);
		try {
			db.getMessageDependencies(transaction, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(true);
		try {
			db.getMessageDependents(transaction, messageId);
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfTransportIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// registerLocalAuthor()
			oneOf(database).containsLocalAuthor(txn, localAuthor.getId());
			will(returnValue(false));
			oneOf(database).addLocalAuthor(txn, localAuthor);
			oneOf(eventBus).broadcast(with(any(LocalAuthorAddedEvent.class)));
			// addContact()
			oneOf(database).containsLocalAuthor(txn, localAuthor.getId());
			will(returnValue(true));
			oneOf(database).containsLocalAuthor(txn, author.getId());
			will(returnValue(false));
			oneOf(database).containsContact(txn, author.getId(),
					localAuthor.getId());
			will(returnValue(false));
			oneOf(database).addContact(txn, author, localAuthor.getId(),
					true, true);
			will(returnValue(contactId));
			oneOf(eventBus).broadcast(with(any(ContactAddedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					ContactStatusChangedEvent.class)));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
			// Check whether the transport is in the DB (which it's not)
			exactly(5).of(database).startTransaction();
			will(returnValue(txn));
			exactly(5).of(database).containsTransport(txn, transportId);
			will(returnValue(false));
			exactly(5).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.addLocalAuthor(transaction, localAuthor);
			assertEquals(contactId, db.addContact(transaction, author,
					localAuthor.getId(), true, true));
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.getTransportKeys(transaction, transportId);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.incrementStreamCounter(transaction, transportId, keySetId);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.removeTransport(transaction, transportId);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.removeTransportKeys(transaction, transportId, keySetId);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}

		transaction = db.startTransaction(false);
		try {
			db.setReorderingWindow(transaction, keySetId, transportId, 0, 0,
					new byte[REORDERING_WINDOW_SIZE / 8]);
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testGenerateAck() throws Exception {
		Collection<MessageId> messagesToAck = Arrays.asList(messageId,
				messageId1);
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

		Transaction transaction = db.startTransaction(false);
		try {
			Ack a = db.generateAck(transaction, contactId, 123);
			assertNotNull(a);
			assertEquals(messagesToAck, a.getMessageIds());
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testGenerateBatch() throws Exception {
		byte[] raw1 = new byte[size];
		Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		Collection<byte[]> messages = Arrays.asList(raw, raw1);
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

		Transaction transaction = db.startTransaction(false);
		try {
			assertEquals(messages, db.generateBatch(transaction, contactId,
					size * 2, maxLatency));
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testGenerateOffer() throws Exception {
		MessageId messageId1 = new MessageId(getRandomId());
		Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
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

		Transaction transaction = db.startTransaction(false);
		try {
			Offer o = db.generateOffer(transaction, contactId, 123, maxLatency);
			assertNotNull(o);
			assertEquals(ids, o.getMessageIds());
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testGenerateRequest() throws Exception {
		MessageId messageId1 = new MessageId(getRandomId());
		Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
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

		Transaction transaction = db.startTransaction(false);
		try {
			Request r = db.generateRequest(transaction, contactId, 123);
			assertNotNull(r);
			assertEquals(ids, r.getMessageIds());
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testGenerateRequestedBatch() throws Exception {
		byte[] raw1 = new byte[size];
		Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		Collection<byte[]> messages = Arrays.asList(raw, raw1);
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

		Transaction transaction = db.startTransaction(false);
		try {
			assertEquals(messages, db.generateRequestedBatch(transaction,
					contactId, size * 2, maxLatency));
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testReceiveAck() throws Exception {
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

		Transaction transaction = db.startTransaction(false);
		try {
			Ack a = new Ack(singletonList(messageId));
			db.receiveAck(transaction, contactId, a);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testReceiveMessage() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// First time
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(VISIBLE));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).addMessage(txn, message, UNKNOWN, false, contactId);
			// Second time
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(VISIBLE));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).raiseSeenFlag(txn, contactId, messageId);
			oneOf(database).raiseAckFlag(txn, contactId, messageId);
			oneOf(database).commitTransaction(txn);
			// First time: the message was received and added
			oneOf(eventBus).broadcast(with(any(MessageToAckEvent.class)));
			oneOf(eventBus).broadcast(with(any(MessageAddedEvent.class)));
			// Second time: the message needs to be acked
			oneOf(eventBus).broadcast(with(any(MessageToAckEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			// Receive the message twice
			db.receiveMessage(transaction, contactId, message);
			db.receiveMessage(transaction, contactId, message);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testReceiveDuplicateMessage() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(VISIBLE));
			// The message wasn't stored but it must still be acked
			oneOf(database).raiseSeenFlag(txn, contactId, messageId);
			oneOf(database).raiseAckFlag(txn, contactId, messageId);
			oneOf(database).commitTransaction(txn);
			// The message was received but not added
			oneOf(eventBus).broadcast(with(any(MessageToAckEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.receiveMessage(transaction, contactId, message);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testReceiveMessageWithoutVisibleGroup() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(INVISIBLE));
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.receiveMessage(transaction, contactId, message);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testReceiveOffer() throws Exception {
		MessageId messageId1 = new MessageId(getRandomId());
		MessageId messageId2 = new MessageId(getRandomId());
		MessageId messageId3 = new MessageId(getRandomId());
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

		Transaction transaction = db.startTransaction(false);
		try {
			Offer o = new Offer(Arrays.asList(messageId, messageId1,
					messageId2, messageId3));
			db.receiveOffer(transaction, contactId, o);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testReceiveRequest() throws Exception {
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

		Transaction transaction = db.startTransaction(false);
		try {
			Request r = new Request(singletonList(messageId));
			db.receiveRequest(transaction, contactId, r);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testChangingVisibilityFromInvisibleToVisibleCallsListeners()
			throws Exception {
		AtomicReference<GroupVisibilityUpdatedEvent> event =
				new AtomicReference<>();

		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(INVISIBLE));
			oneOf(database).addGroupVisibility(txn, contactId, groupId, false);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(
					GroupVisibilityUpdatedEvent.class)));
			will(new CaptureArgumentAction<>(event,
					GroupVisibilityUpdatedEvent.class, 0));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.setGroupVisibility(transaction, contactId, groupId, VISIBLE);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}

		GroupVisibilityUpdatedEvent e = event.get();
		assertNotNull(e);
		assertEquals(singletonList(contactId), e.getAffectedContacts());
	}

	@Test
	public void testChangingVisibilityFromVisibleToInvisibleCallsListeners()
			throws Exception {
		AtomicReference<GroupVisibilityUpdatedEvent> event =
				new AtomicReference<>();

		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(VISIBLE));
			oneOf(database).removeGroupVisibility(txn, contactId, groupId);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(
					GroupVisibilityUpdatedEvent.class)));
			will(new CaptureArgumentAction<>(event,
					GroupVisibilityUpdatedEvent.class, 0));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.setGroupVisibility(transaction, contactId, groupId, INVISIBLE);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}

		GroupVisibilityUpdatedEvent e = event.get();
		assertNotNull(e);
		assertEquals(singletonList(contactId), e.getAffectedContacts());
	}

	@Test
	public void testNotChangingVisibilityDoesNotCallListeners()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(VISIBLE)); // Already visible
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.setGroupVisibility(transaction, contactId, groupId, VISIBLE);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testTransportKeys() throws Exception {
		TransportKeys transportKeys = createTransportKeys();
		KeySet ks = new KeySet(keySetId, contactId, transportKeys);
		Collection<KeySet> keys = singletonList(ks);

		context.checking(new Expectations() {{
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// updateTransportKeys()
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).updateTransportKeys(txn, ks);
			// getTransportKeys()
			oneOf(database).containsTransport(txn, transportId);
			will(returnValue(true));
			oneOf(database).getTransportKeys(txn, transportId);
			will(returnValue(keys));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.updateTransportKeys(transaction, keys);
			assertEquals(keys, db.getTransportKeys(transaction, transportId));
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testGetMessageStatusByGroupId() throws Exception {
		MessageStatus status =
				new MessageStatus(messageId, contactId, true, true);

		context.checking(new Expectations() {{
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// getMessageStatus()
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(VISIBLE));
			oneOf(database).getMessageStatus(txn, contactId, groupId);
			will(returnValue(singletonList(status)));
			// getMessageStatus() again
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).getGroupVisibility(txn, contactId, groupId);
			will(returnValue(INVISIBLE));
			oneOf(database).getMessageIds(txn, groupId);
			will(returnValue(singletonList(messageId)));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(true);
		try {
			// With visible group - return stored status
			Collection<MessageStatus> statuses =
					db.getMessageStatus(transaction, contactId, groupId);
			assertEquals(1, statuses.size());
			MessageStatus s = statuses.iterator().next();
			assertEquals(messageId, s.getMessageId());
			assertEquals(contactId, s.getContactId());
			assertTrue(s.isSent());
			assertTrue(s.isSeen());
			// With invisible group - return default status
			statuses = db.getMessageStatus(transaction, contactId, groupId);
			assertEquals(1, statuses.size());
			s = statuses.iterator().next();
			assertEquals(messageId, s.getMessageId());
			assertEquals(contactId, s.getContactId());
			assertFalse(s.isSent());
			assertFalse(s.isSeen());
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testGetMessageStatusByMessageId() throws Exception {
		MessageStatus status =
				new MessageStatus(messageId, contactId, true, true);

		context.checking(new Expectations() {{
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// getMessageStatus()
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).getMessageStatus(txn, contactId, messageId);
			will(returnValue(status));
			// getMessageStatus() again
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).getMessageStatus(txn, contactId, messageId);
			will(returnValue(null));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(true);
		try {
			// With visible group - return stored status
			MessageStatus s =
					db.getMessageStatus(transaction, contactId, messageId);
			assertEquals(messageId, s.getMessageId());
			assertEquals(contactId, s.getContactId());
			assertTrue(s.isSent());
			assertTrue(s.isSeen());
			// With invisible group - return default status
			s = db.getMessageStatus(transaction, contactId, messageId);
			assertEquals(messageId, s.getMessageId());
			assertEquals(contactId, s.getContactId());
			assertFalse(s.isSent());
			assertFalse(s.isSeen());
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	private TransportKeys createTransportKeys() {
		SecretKey inPrevTagKey = getSecretKey();
		SecretKey inPrevHeaderKey = getSecretKey();
		IncomingKeys inPrev = new IncomingKeys(inPrevTagKey, inPrevHeaderKey,
				1, 123, new byte[4]);
		SecretKey inCurrTagKey = getSecretKey();
		SecretKey inCurrHeaderKey = getSecretKey();
		IncomingKeys inCurr = new IncomingKeys(inCurrTagKey, inCurrHeaderKey,
				2, 234, new byte[4]);
		SecretKey inNextTagKey = getSecretKey();
		SecretKey inNextHeaderKey = getSecretKey();
		IncomingKeys inNext = new IncomingKeys(inNextTagKey, inNextHeaderKey,
				3, 345, new byte[4]);
		SecretKey outCurrTagKey = getSecretKey();
		SecretKey outCurrHeaderKey = getSecretKey();
		OutgoingKeys outCurr = new OutgoingKeys(outCurrTagKey, outCurrHeaderKey,
				2, 456, true);
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr);
	}

	@Test
	public void testMergeSettings() throws Exception {
		Settings before = new Settings();
		before.put("foo", "bar");
		before.put("baz", "bam");
		Settings update = new Settings();
		update.put("baz", "qux");
		Settings merged = new Settings();
		merged.put("foo", "bar");
		merged.put("baz", "qux");
		context.checking(new Expectations() {{
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// mergeSettings()
			oneOf(database).getSettings(txn, "namespace");
			will(returnValue(before));
			oneOf(database).mergeSettings(txn, update, "namespace");
			oneOf(eventBus).broadcast(with(any(SettingsUpdatedEvent.class)));
			// mergeSettings() again
			oneOf(database).getSettings(txn, "namespace");
			will(returnValue(merged));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
		}});

		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			// First merge should broadcast an event
			db.mergeSettings(transaction, update, "namespace");
			// Second merge should not broadcast an event
			db.mergeSettings(transaction, update, "namespace");
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testCannotStartReadTransactionDuringReadTransaction()
			throws Exception {
		testCannotStartTransactionDuringTransaction(true, true);
	}

	@Test(expected = IllegalStateException.class)
	public void testCannotStartWriteTransactionDuringReadTransaction()
			throws Exception {
		testCannotStartTransactionDuringTransaction(true, false);
	}

	@Test(expected = IllegalStateException.class)
	public void testCannotStartReadTransactionDuringWriteTransaction()
			throws Exception {
		testCannotStartTransactionDuringTransaction(false, true);
	}

	@Test(expected = IllegalStateException.class)
	public void testCannotStartWriteTransactionDuringWriteTransaction()
			throws Exception {
		testCannotStartTransactionDuringTransaction(false, false);
	}

	private void testCannotStartTransactionDuringTransaction(
			boolean firstTxnReadOnly, boolean secondTxnReadOnly)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
		}});

		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		assertNotNull(db.startTransaction(firstTxnReadOnly));
		db.startTransaction(secondTxnReadOnly);
		fail();
	}

	@Test
	public void testCannotAddLocalIdentityAsContact() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsLocalAuthor(txn, localAuthor.getId());
			will(returnValue(true));
			// Contact is a local identity
			oneOf(database).containsLocalAuthor(txn, author.getId());
			will(returnValue(true));
			oneOf(database).abortTransaction(txn);
		}});

		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.addContact(transaction, author, localAuthor.getId(), true, true);
			fail();
		} catch (ContactExistsException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	public void testCannotAddDuplicateContact() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsLocalAuthor(txn, localAuthor.getId());
			will(returnValue(true));
			oneOf(database).containsLocalAuthor(txn, author.getId());
			will(returnValue(false));
			// Contact already exists for this local identity
			oneOf(database).containsContact(txn, author.getId(),
					localAuthor.getId());
			will(returnValue(true));
			oneOf(database).abortTransaction(txn);
		}});

		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		Transaction transaction = db.startTransaction(false);
		try {
			db.addContact(transaction, author, localAuthor.getId(), true, true);
			fail();
		} catch (ContactExistsException expected) {
			// Expected
		} finally {
			db.endTransaction(transaction);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMessageDependencies() throws Exception {
		int shutdownHandle = 12345;
		MessageId messageId2 = new MessageId(getRandomId());
		context.checking(new Expectations() {{
			// open()
			oneOf(database).open(null);
			will(returnValue(false));
			oneOf(shutdown).addShutdownHook(with(any(Runnable.class)));
			will(returnValue(shutdownHandle));
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// addLocalMessage()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).addMessage(txn, message, DELIVERED, true, null);
			oneOf(database).mergeMessageMetadata(txn, messageId, metadata);
			// addMessageDependencies()
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).getMessageState(txn, messageId);
			will(returnValue(DELIVERED));
			oneOf(database).addMessageDependency(txn, message, messageId1,
					DELIVERED);
			oneOf(database).addMessageDependency(txn, message, messageId2,
					DELIVERED);
			// getMessageDependencies()
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).getMessageDependencies(txn, messageId);
			// getMessageDependents()
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(true));
			oneOf(database).getMessageDependents(txn, messageId);
			// broadcast for message added event
			oneOf(eventBus).broadcast(with(any(MessageAddedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					MessageStateChangedEvent.class)));
			oneOf(eventBus).broadcast(with(any(MessageSharedEvent.class)));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
			// close()
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				shutdown);

		assertFalse(db.open(null));
		Transaction transaction = db.startTransaction(false);
		try {
			db.addLocalMessage(transaction, message, metadata, true);
			Collection<MessageId> dependencies = new ArrayList<>(2);
			dependencies.add(messageId1);
			dependencies.add(messageId2);
			db.addMessageDependencies(transaction, message, dependencies);
			db.getMessageDependencies(transaction, messageId);
			db.getMessageDependents(transaction, messageId);
			db.commitTransaction(transaction);
		} finally {
			db.endTransaction(transaction);
		}
		db.close();
	}
}
