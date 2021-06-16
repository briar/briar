package org.briarproject.bramble.db;

import org.briarproject.bramble.api.cleanup.event.CleanupTimerStartedEvent;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.NoSuchGroupException;
import org.briarproject.bramble.api.db.NoSuchIdentityException;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.db.NoSuchPendingContactException;
import org.briarproject.bramble.api.db.NoSuchTransportException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.identity.event.IdentityAddedEvent;
import org.briarproject.bramble.api.identity.event.IdentityRemovedEvent;
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
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeySet;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.briarproject.bramble.api.db.DatabaseComponent.TIMER_NOT_STARTED;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_LENGTH;
import static org.briarproject.bramble.api.sync.validation.MessageState.DELIVERED;
import static org.briarproject.bramble.api.sync.validation.MessageState.UNKNOWN;
import static org.briarproject.bramble.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;
import static org.briarproject.bramble.db.DatabaseConstants.MAX_OFFERED_MESSAGES;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getIdentity;
import static org.briarproject.bramble.test.TestUtils.getMessage;
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
	private final ShutdownManager shutdownManager =
			context.mock(ShutdownManager.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final Executor eventExecutor = context.mock(Executor.class);

	private final SecretKey key = getSecretKey();
	private final Object txn = new Object();
	private final ClientId clientId;
	private final int majorVersion;
	private final GroupId groupId;
	private final Group group;
	private final Author author;
	private final Identity identity;
	private final LocalAuthor localAuthor;
	private final String alias;
	private final Message message, message1;
	private final MessageId messageId, messageId1;
	private final Metadata metadata;
	private final TransportId transportId;
	private final int maxLatency;
	private final ContactId contactId;
	private final Contact contact;
	private final KeySetId keySetId;
	private final PendingContactId pendingContactId;
	private final Random random = new Random();
	private final boolean shared = random.nextBoolean();
	private final boolean temporary = random.nextBoolean();

	public DatabaseComponentImplTest() {
		clientId = getClientId();
		majorVersion = 123;
		group = getGroup(clientId, majorVersion);
		groupId = group.getId();
		author = getAuthor();
		identity = getIdentity();
		localAuthor = identity.getLocalAuthor();
		message = getMessage(groupId);
		message1 = getMessage(groupId);
		messageId = message.getId();
		messageId1 = message1.getId();
		metadata = new Metadata();
		metadata.put("foo", new byte[] {'b', 'a', 'r'});
		transportId = getTransportId();
		maxLatency = Integer.MAX_VALUE;
		contact = getContact(author, localAuthor.getId(), true);
		contactId = contact.getId();
		alias = contact.getAlias();
		keySetId = new KeySetId(345);
		pendingContactId = new PendingContactId(getRandomId());
	}

	private DatabaseComponent createDatabaseComponent(Database<Object> database,
			EventBus eventBus, Executor eventExecutor,
			ShutdownManager shutdownManager) {
		return new DatabaseComponentImpl<>(database, Object.class, eventBus,
				eventExecutor, shutdownManager);
	}

	@Test
	public void testSimpleCalls() throws Exception {
		int shutdownHandle = 12345;
		context.checking(new Expectations() {{
			// open()
			oneOf(database).open(key, null);
			will(returnValue(false));
			oneOf(shutdownManager).addShutdownHook(with(any(Runnable.class)));
			will(returnValue(shutdownHandle));
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// addIdentity()
			oneOf(database).containsIdentity(txn, localAuthor.getId());
			will(returnValue(false));
			oneOf(database).addIdentity(txn, identity);
			oneOf(eventBus).broadcast(with(any(IdentityAddedEvent.class)));
			// addContact()
			oneOf(database).containsIdentity(txn, localAuthor.getId());
			will(returnValue(true));
			oneOf(database).containsIdentity(txn, author.getId());
			will(returnValue(false));
			oneOf(database).containsContact(txn, author.getId(),
					localAuthor.getId());
			will(returnValue(false));
			oneOf(database).addContact(txn, author, localAuthor.getId(),
					null, true);
			will(returnValue(contactId));
			oneOf(eventBus).broadcast(with(any(ContactAddedEvent.class)));
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
			// removeIdentity()
			oneOf(database).containsIdentity(txn, localAuthor.getId());
			will(returnValue(true));
			oneOf(database).removeIdentity(txn, localAuthor.getId());
			oneOf(eventBus).broadcast(with(any(IdentityRemovedEvent.class)));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
			// close()
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		assertFalse(db.open(key, null));
		db.transaction(false, transaction -> {
			db.addIdentity(transaction, identity);
			assertEquals(contactId, db.addContact(transaction, author,
					localAuthor.getId(), null, true));
			assertEquals(singletonList(contact),
					db.getContacts(transaction));
			db.addGroup(transaction, group); // First time - listeners called
			db.addGroup(transaction, group); // Second time - not called
			assertEquals(singletonList(group),
					db.getGroups(transaction, clientId, majorVersion));
			db.removeGroup(transaction, group);
			db.removeContact(transaction, contactId);
			db.removeIdentity(transaction, localAuthor.getId());
		});
		db.close();
	}

	@Test(expected = NoSuchGroupException.class)
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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				db.addLocalMessage(transaction, message, metadata, shared,
						temporary));
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
			oneOf(database).addMessage(txn, message, DELIVERED, shared,
					temporary, null);
			oneOf(database).mergeMessageMetadata(txn, messageId, metadata);
			oneOf(database).commitTransaction(txn);
			// The message was added, so the listeners should be called
			oneOf(eventBus).broadcast(with(any(MessageAddedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					MessageStateChangedEvent.class)));
			if (shared)
				oneOf(eventBus).broadcast(with(any(MessageSharedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				db.addLocalMessage(transaction, message, metadata, shared,
						temporary));
	}

	@Test
	public void testVariousMethodsThrowExceptionIfContactIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the contact is in the DB (which it's not)
			exactly(19).of(database).startTransaction();
			will(returnValue(txn));
			exactly(19).of(database).containsContact(txn, contactId);
			will(returnValue(false));
			exactly(19).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		try {
			db.transaction(false, transaction ->
					db.addTransportKeys(transaction, contactId,
							createTransportKeys()));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.generateAck(transaction, contactId, 123));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.generateBatch(transaction, contactId, 123, 456));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.generateOffer(transaction, contactId, 123, 456));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.generateRequest(transaction, contactId, 123));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getContact(transaction, contactId));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getUnackedMessageBytesToSend(transaction, contactId));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageStatus(transaction, contactId, groupId));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageStatus(transaction, contactId, messageId));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getGroupVisibility(transaction, contactId, groupId));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getSyncVersions(transaction, contactId));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			Ack a = new Ack(singletonList(messageId));
			db.transaction(false, transaction ->
					db.receiveAck(transaction, contactId, a));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.receiveMessage(transaction, contactId, message));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			Offer o = new Offer(singletonList(messageId));
			db.transaction(false, transaction ->
					db.receiveOffer(transaction, contactId, o));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			Request r = new Request(singletonList(messageId));
			db.transaction(false, transaction ->
					db.receiveRequest(transaction, contactId, r));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.removeContact(transaction, contactId));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setContactAlias(transaction, contactId, alias));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setGroupVisibility(transaction, contactId, groupId,
							SHARED));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setSyncVersions(transaction, contactId, emptyList()));
			fail();
		} catch (NoSuchContactException expected) {
			// Expected
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfIdentityIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the identity is in the DB (which it's not)
			exactly(4).of(database).startTransaction();
			will(returnValue(txn));
			exactly(4).of(database).containsIdentity(txn, localAuthor.getId());
			will(returnValue(false));
			exactly(4).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		try {
			db.transaction(false, transaction ->
					db.addContact(transaction, author, localAuthor.getId(),
							null, true));
			fail();
		} catch (NoSuchIdentityException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.getIdentity(transaction, localAuthor.getId()));
			fail();
		} catch (NoSuchIdentityException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.removeIdentity(transaction, localAuthor.getId()));
			fail();
		} catch (NoSuchIdentityException expected) {
			// Expected
		}

		try {
			PublicKey publicKey = getAgreementPublicKey();
			PrivateKey privateKey = getAgreementPrivateKey();
			db.transaction(false, transaction ->
					db.setHandshakeKeyPair(transaction, localAuthor.getId(),
							publicKey, privateKey));
			fail();
		} catch (NoSuchIdentityException expected) {
			// Expected
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfGroupIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the group is in the DB (which it's not)
			exactly(10).of(database).startTransaction();
			will(returnValue(txn));
			exactly(10).of(database).containsGroup(txn, groupId);
			will(returnValue(false));
			exactly(10).of(database).abortTransaction(txn);
			// Allow other checks to pass
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		try {
			db.transaction(true, transaction ->
					db.getGroup(transaction, groupId));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getGroupMetadata(transaction, groupId));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageIds(transaction, groupId));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageIds(transaction, groupId, new Metadata()));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageMetadata(transaction, groupId));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageMetadata(transaction, groupId,
							new Metadata()));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageStatus(transaction, contactId, groupId));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.mergeGroupMetadata(transaction, groupId, metadata));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.removeGroup(transaction, group));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setGroupVisibility(transaction, contactId, groupId,
							SHARED));
			fail();
		} catch (NoSuchGroupException expected) {
			// Expected
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfMessageIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the message is in the DB (which it's not)
			exactly(15).of(database).startTransaction();
			will(returnValue(txn));
			exactly(15).of(database).containsMessage(txn, messageId);
			will(returnValue(false));
			exactly(15).of(database).abortTransaction(txn);
			// Allow other checks to pass
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		try {
			db.transaction(false, transaction ->
					db.deleteMessage(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.deleteMessageMetadata(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessage(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageMetadata(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageState(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageStatus(transaction, contactId, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.mergeMessageMetadata(transaction, messageId, metadata));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setCleanupTimerDuration(transaction, message.getId(),
							HOURS.toMillis(1)));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setMessagePermanent(transaction, message.getId()));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setMessageShared(transaction, message.getId()));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setMessageState(transaction, messageId, DELIVERED));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageDependencies(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(true, transaction ->
					db.getMessageDependents(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.startCleanupTimer(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.stopCleanupTimer(transaction, messageId));
			fail();
		} catch (NoSuchMessageException expected) {
			// Expected
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfTransportIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the transport is in the DB (which it's not)
			exactly(8).of(database).startTransaction();
			will(returnValue(txn));
			exactly(8).of(database).containsTransport(txn, transportId);
			will(returnValue(false));
			exactly(8).of(database).abortTransaction(txn);
			// Allow other checks to pass
			allowing(database).containsContact(txn, contactId);
			will(returnValue(true));
			allowing(database).containsPendingContact(txn, pendingContactId);
			will(returnValue(true));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		try {
			db.transaction(false, transaction ->
					db.addTransportKeys(transaction, contactId,
							createHandshakeKeys()));
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.addTransportKeys(transaction, pendingContactId,
							createHandshakeKeys()));
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.getTransportKeys(transaction, transportId));
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.incrementStreamCounter(transaction, transportId,
							keySetId));
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.removeTransportKeys(transaction, transportId, keySetId));
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.removeTransport(transaction, transportId));
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setReorderingWindow(transaction, keySetId, transportId,
							0, 0, new byte[REORDERING_WINDOW_SIZE / 8]));
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.setTransportKeysActive(transaction, transportId,
							keySetId));
			fail();
		} catch (NoSuchTransportException expected) {
			// Expected
		}
	}

	@Test
	public void testVariousMethodsThrowExceptionIfPendingContactIsMissing()
			throws Exception {
		context.checking(new Expectations() {{
			// Check whether the pending contact is in the DB (which it's not)
			exactly(3).of(database).startTransaction();
			will(returnValue(txn));
			exactly(3).of(database).containsPendingContact(txn,
					pendingContactId);
			will(returnValue(false));
			exactly(3).of(database).abortTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		try {
			db.transaction(false, transaction ->
					db.addTransportKeys(transaction, pendingContactId,
							createHandshakeKeys()));
			fail();
		} catch (NoSuchPendingContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.getPendingContact(transaction, pendingContactId));
			fail();
		} catch (NoSuchPendingContactException expected) {
			// Expected
		}

		try {
			db.transaction(false, transaction ->
					db.removePendingContact(transaction, pendingContactId));
			fail();
		} catch (NoSuchPendingContactException expected) {
			// Expected
		}
	}

	@Test
	public void testGenerateAck() throws Exception {
		Collection<MessageId> messagesToAck = asList(messageId, messageId1);
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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			Ack a = db.generateAck(transaction, contactId, 123);
			assertNotNull(a);
			assertEquals(messagesToAck, a.getMessageIds());
		});
	}

	@Test
	public void testGenerateBatch() throws Exception {
		Collection<MessageId> ids = asList(messageId, messageId1);
		Collection<Message> messages = asList(message, message1);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getMessagesToSend(txn, contactId,
					MAX_MESSAGE_LENGTH * 2, maxLatency);
			will(returnValue(ids));
			oneOf(database).getMessage(txn, messageId);
			will(returnValue(message));
			oneOf(database).updateExpiryTimeAndEta(txn, contactId, messageId,
					maxLatency);
			oneOf(database).getMessage(txn, messageId1);
			will(returnValue(message1));
			oneOf(database).updateExpiryTimeAndEta(txn, contactId, messageId1,
					maxLatency);
			oneOf(database).lowerRequestedFlag(txn, contactId, ids);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(MessagesSentEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				assertEquals(messages, db.generateBatch(transaction, contactId,
						MAX_MESSAGE_LENGTH * 2, maxLatency)));
	}

	@Test
	public void testGenerateOffer() throws Exception {
		MessageId messageId1 = new MessageId(getRandomId());
		Collection<MessageId> ids = asList(messageId, messageId1);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getMessagesToOffer(txn, contactId, 123, maxLatency);
			will(returnValue(ids));
			oneOf(database).updateExpiryTimeAndEta(txn, contactId, messageId,
					maxLatency);
			oneOf(database).updateExpiryTimeAndEta(txn, contactId, messageId1,
					maxLatency);
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			Offer o = db.generateOffer(transaction, contactId, 123, maxLatency);
			assertNotNull(o);
			assertEquals(ids, o.getMessageIds());
		});
	}

	@Test
	public void testGenerateRequest() throws Exception {
		MessageId messageId1 = new MessageId(getRandomId());
		Collection<MessageId> ids = asList(messageId, messageId1);
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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			Request r = db.generateRequest(transaction, contactId, 123);
			assertNotNull(r);
			assertEquals(ids, r.getMessageIds());
		});
	}

	@Test
	public void testGenerateRequestedBatch() throws Exception {
		Collection<MessageId> ids = asList(messageId, messageId1);
		Collection<Message> messages = asList(message, message1);
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).getRequestedMessagesToSend(txn, contactId,
					MAX_MESSAGE_LENGTH * 2, maxLatency);
			will(returnValue(ids));
			oneOf(database).getMessage(txn, messageId);
			will(returnValue(message));
			oneOf(database).updateExpiryTimeAndEta(txn, contactId, messageId,
					maxLatency);
			oneOf(database).getMessage(txn, messageId1);
			will(returnValue(message1));
			oneOf(database).updateExpiryTimeAndEta(txn, contactId, messageId1,
					maxLatency);
			oneOf(database).lowerRequestedFlag(txn, contactId, ids);
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(MessagesSentEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				assertEquals(messages, db.generateRequestedBatch(transaction,
						contactId, MAX_MESSAGE_LENGTH * 2, maxLatency)));
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
			will(returnValue(true));
			oneOf(database).startCleanupTimer(txn, messageId);
			will(returnValue(TIMER_NOT_STARTED)); // No cleanup duration was set
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(MessagesAckedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			Ack a = new Ack(singletonList(messageId));
			db.receiveAck(transaction, contactId, a);
		});
	}

	@Test
	public void testReceiveDuplicateAck() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsVisibleMessage(txn, contactId, messageId);
			will(returnValue(true));
			oneOf(database).raiseSeenFlag(txn, contactId, messageId);
			will(returnValue(false)); // Already acked
			oneOf(database).commitTransaction(txn);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			Ack a = new Ack(singletonList(messageId));
			db.receiveAck(transaction, contactId, a);
		});
	}

	@Test
	public void testReceiveAckWithCleanupTimer() throws Exception {
		long deadline = System.currentTimeMillis();
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsContact(txn, contactId);
			will(returnValue(true));
			oneOf(database).containsVisibleMessage(txn, contactId, messageId);
			will(returnValue(true));
			oneOf(database).raiseSeenFlag(txn, contactId, messageId);
			will(returnValue(true));
			oneOf(database).startCleanupTimer(txn, messageId);
			will(returnValue(deadline));
			oneOf(database).commitTransaction(txn);
			oneOf(eventBus).broadcast(with(any(
					CleanupTimerStartedEvent.class)));
			oneOf(eventBus).broadcast(with(any(MessagesAckedEvent.class)));
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			Ack a = new Ack(singletonList(messageId));
			db.receiveAck(transaction, contactId, a);
		});
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
			oneOf(database).addMessage(txn, message, UNKNOWN, false, false,
					contactId);
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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			// Receive the message twice
			db.receiveMessage(transaction, contactId, message);
			db.receiveMessage(transaction, contactId, message);
		});
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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				db.receiveMessage(transaction, contactId, message));
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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				db.receiveMessage(transaction, contactId, message));
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
				eventExecutor, shutdownManager);

		Offer o = new Offer(asList(messageId, messageId1,
				messageId2, messageId3));
		db.transaction(false, transaction ->
				db.receiveOffer(transaction, contactId, o));
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
				eventExecutor, shutdownManager);

		Request r = new Request(singletonList(messageId));
		db.transaction(false, transaction ->
				db.receiveRequest(transaction, contactId, r));
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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				db.setGroupVisibility(transaction, contactId, groupId,
						VISIBLE));

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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				db.setGroupVisibility(transaction, contactId, groupId,
						INVISIBLE));

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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction ->
				db.setGroupVisibility(transaction, contactId, groupId,
						VISIBLE));
	}

	@Test
	public void testTransportKeys() throws Exception {
		TransportKeys transportKeys = createTransportKeys();
		TransportKeySet ks =
				new TransportKeySet(keySetId, contactId, null, transportKeys);
		Collection<TransportKeySet> keys = singletonList(ks);

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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			db.updateTransportKeys(transaction, keys);
			assertEquals(keys, db.getTransportKeys(transaction, transportId));
		});
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
				eventExecutor, shutdownManager);

		db.transaction(true, transaction -> {
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
		});
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
				eventExecutor, shutdownManager);

		db.transaction(true, transaction -> {
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
		});
	}

	private TransportKeys createHandshakeKeys() {
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
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr,
				getSecretKey(), true);
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
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			// First merge should broadcast an event
			db.mergeSettings(transaction, update, "namespace");
			// Second merge should not broadcast an event
			db.mergeSettings(transaction, update, "namespace");
		});
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
				eventExecutor, shutdownManager);

		assertNotNull(db.startTransaction(firstTxnReadOnly));
		db.startTransaction(secondTxnReadOnly);
		fail();
	}

	@Test
	public void testCannotAddLocalIdentityAsContact() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsIdentity(txn, localAuthor.getId());
			will(returnValue(true));
			// Contact is a local identity
			oneOf(database).containsIdentity(txn, author.getId());
			will(returnValue(true));
			oneOf(database).abortTransaction(txn);
		}});

		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		try {
			db.transaction(false, transaction ->
					db.addContact(transaction, author, localAuthor.getId(),
							null, true));
			fail();
		} catch (ContactExistsException expected) {
			assertEquals(localAuthor.getId(), expected.getLocalAuthorId());
			assertEquals(author, expected.getRemoteAuthor());
		}
	}

	@Test
	public void testCannotAddDuplicateContact() throws Exception {
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			oneOf(database).containsIdentity(txn, localAuthor.getId());
			will(returnValue(true));
			oneOf(database).containsIdentity(txn, author.getId());
			will(returnValue(false));
			// Contact already exists for this local identity
			oneOf(database).containsContact(txn, author.getId(),
					localAuthor.getId());
			will(returnValue(true));
			oneOf(database).abortTransaction(txn);
		}});

		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		try {
			db.transaction(false, transaction ->
					db.addContact(transaction, author, localAuthor.getId(),
							null, true));
			fail();
		} catch (ContactExistsException expected) {
			assertEquals(localAuthor.getId(), expected.getLocalAuthorId());
			assertEquals(author, expected.getRemoteAuthor());
		}
	}

	@Test
	public void testMessageDependencies() throws Exception {
		int shutdownHandle = 12345;
		MessageId messageId2 = new MessageId(getRandomId());

		context.checking(new Expectations() {{
			// open()
			oneOf(database).open(key, null);
			will(returnValue(false));
			oneOf(shutdownManager).addShutdownHook(with(any(Runnable.class)));
			will(returnValue(shutdownHandle));
			// startTransaction()
			oneOf(database).startTransaction();
			will(returnValue(txn));
			// addLocalMessage()
			oneOf(database).containsGroup(txn, groupId);
			will(returnValue(true));
			oneOf(database).containsMessage(txn, messageId);
			will(returnValue(false));
			oneOf(database).addMessage(txn, message, DELIVERED, shared,
					temporary, null);
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
			if (shared)
				oneOf(eventBus).broadcast(with(any(MessageSharedEvent.class)));
			// endTransaction()
			oneOf(database).commitTransaction(txn);
			// close()
			oneOf(database).close();
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		assertFalse(db.open(key, null));
		db.transaction(false, transaction -> {
			db.addLocalMessage(transaction, message, metadata, shared,
					temporary);
			Collection<MessageId> dependencies = new ArrayList<>(2);
			dependencies.add(messageId1);
			dependencies.add(messageId2);
			db.addMessageDependencies(transaction, message, dependencies);
			db.getMessageDependencies(transaction, messageId);
			db.getMessageDependents(transaction, messageId);
		});
		db.close();
	}

	@Test
	public void testCommitActionsOccurInOrder() throws Exception {
		TestEvent action1 = new TestEvent();
		Runnable action2 = () -> {
		};
		TestEvent action3 = new TestEvent();
		Runnable action4 = () -> {
		};

		Sequence sequence = context.sequence("sequence");
		context.checking(new Expectations() {{
			oneOf(database).startTransaction();
			will(returnValue(txn));
			inSequence(sequence);
			oneOf(database).commitTransaction(txn);
			inSequence(sequence);
			oneOf(eventBus).broadcast(action1);
			inSequence(sequence);
			oneOf(eventExecutor).execute(action2);
			inSequence(sequence);
			oneOf(eventBus).broadcast(action3);
			inSequence(sequence);
			oneOf(eventExecutor).execute(action4);
			inSequence(sequence);
		}});
		DatabaseComponent db = createDatabaseComponent(database, eventBus,
				eventExecutor, shutdownManager);

		db.transaction(false, transaction -> {
			transaction.attach(action1);
			transaction.attach(action2);
			transaction.attach(action3);
			transaction.attach(action4);
		});
	}

	private static class TestEvent extends Event {
	}
}
