package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.MessageDeletedException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.sync.validation.MessageState;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeySet;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.SettableClock;
import org.briarproject.bramble.test.TestDatabaseConfig;
import org.briarproject.bramble.test.TestMessageFactory;
import org.briarproject.bramble.test.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.db.DatabaseComponent.NO_CLEANUP_DEADLINE;
import static org.briarproject.bramble.api.db.DatabaseComponent.TIMER_NOT_STARTED;
import static org.briarproject.bramble.api.db.Metadata.REMOVE;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.api.sync.validation.MessageState.DELIVERED;
import static org.briarproject.bramble.api.sync.validation.MessageState.INVALID;
import static org.briarproject.bramble.api.sync.validation.MessageState.PENDING;
import static org.briarproject.bramble.api.sync.validation.MessageState.UNKNOWN;
import static org.briarproject.bramble.db.DatabaseConstants.DB_SETTINGS_NAMESPACE;
import static org.briarproject.bramble.db.DatabaseConstants.LAST_COMPACTED_KEY;
import static org.briarproject.bramble.db.DatabaseConstants.MAX_COMPACTION_INTERVAL_MS;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getIdentity;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getPendingContact;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class JdbcDatabaseTest extends BrambleTestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	// All our transports use a maximum latency of 30 seconds
	private static final int MAX_LATENCY = 30 * 1000;

	private final SecretKey key = getSecretKey();
	private final File testDir = getTestDirectory();
	private final GroupId groupId;
	private final ClientId clientId;
	private final int majorVersion;
	private final Group group;
	private final Author author;
	private final Identity identity;
	private final LocalAuthor localAuthor;
	private final Message message;
	private final MessageId messageId;
	private final TransportId transportId;
	private final ContactId contactId;
	private final KeySetId keySetId, keySetId1;
	private final PendingContact pendingContact;
	private final Random random = new Random();

	JdbcDatabaseTest() {
		clientId = getClientId();
		majorVersion = 123;
		group = getGroup(clientId, majorVersion);
		groupId = group.getId();
		author = getAuthor();
		identity = getIdentity();
		localAuthor = identity.getLocalAuthor();
		message = getMessage(groupId);
		messageId = message.getId();
		transportId = getTransportId();
		contactId = new ContactId(1);
		keySetId = new KeySetId(1);
		keySetId1 = new KeySetId(2);
		pendingContact = getPendingContact();
	}

	protected abstract JdbcDatabase createDatabase(DatabaseConfig config,
			MessageFactory messageFactory, Clock clock);

	@Before
	public void setUp() {
		assertTrue(testDir.mkdirs());
	}

	@Test
	public void testPersistence() throws Exception {
		// Store some records
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsGroup(txn, groupId));
		db.addGroup(txn, group);
		assertTrue(db.containsGroup(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.addMessage(txn, message, DELIVERED, true, false, null);
		assertTrue(db.containsMessage(txn, messageId));
		db.commitTransaction(txn);
		db.close();

		// Check that the records are still there
		db = open(true);
		txn = db.startTransaction();
		assertTrue(db.containsContact(txn, contactId));
		assertTrue(db.containsGroup(txn, groupId));
		assertTrue(db.containsMessage(txn, messageId));
		assertArrayEquals(message.getBody(),
				db.getMessage(txn, messageId).getBody());

		// Delete the records
		db.removeMessage(txn, messageId);
		db.removeContact(txn, contactId);
		db.removeGroup(txn, groupId);
		db.commitTransaction(txn);
		db.close();

		// Check that the records are gone
		db = open(true);
		txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		assertFalse(db.containsGroup(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRemovingGroupRemovesMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// Removing the group should remove the message
		assertTrue(db.containsMessage(txn, messageId));
		db.removeGroup(txn, groupId);
		assertFalse(db.containsMessage(txn, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustHaveSeenFlagFalse() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and a shared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// The contact has not seen the message, so it should be sendable
		Collection<MessageId> ids =
				db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		assertEquals(message.getRawLength(),
				db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Changing the status to seen = true should make the message unsendable
		db.raiseSeenFlag(txn, contactId, messageId);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustBeDelivered() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and a shared but unvalidated message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, UNKNOWN, true, false, null);

		// The message has not been validated, so it should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Marking the message delivered should make it sendable
		db.setMessageState(txn, messageId, DELIVERED);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		assertEquals(message.getRawLength(),
				db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Marking the message invalid should make it unsendable
		db.setMessageState(txn, messageId, INVALID);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Marking the message pending should make it unsendable
		db.setMessageState(txn, messageId, PENDING);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustHaveSharedGroup() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, an invisible group and a shared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// The group is invisible, so the message should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Making the group visible should not make the message sendable
		db.addGroupVisibility(txn, contactId, groupId, false);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Sharing the group should make the message sendable
		db.setGroupVisibility(txn, contactId, groupId, true);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		assertEquals(message.getRawLength(),
				db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Unsharing the group should make the message unsendable
		db.setGroupVisibility(txn, contactId, groupId, false);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Making the group invisible should make the message unsendable
		db.removeGroupVisibility(txn, contactId, groupId);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustBeShared() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and an unshared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, DELIVERED, false, false, null);

		// The message is not shared, so it should not be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(0, db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// Sharing the message should make it sendable
		db.setMessageShared(txn, messageId, true);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		assertEquals(message.getRawLength(),
				db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableMessagesMustFitCapacity() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and a shared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// The message is sendable, but too large to send
		Collection<MessageId> ids =
				db.getMessagesToSend(txn, contactId, message.getRawLength() - 1,
						MAX_LATENCY);
		assertTrue(ids.isEmpty());
		assertEquals(message.getRawLength(),
				db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		// The message is just the right size to send
		ids = db.getMessagesToSend(txn, contactId, message.getRawLength(),
				MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		assertEquals(message.getRawLength(),
				db.getMessageBytesToSend(txn, contactId, MAX_LATENCY));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessagesToAck() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and a visible group
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, false);

		// Add some messages to ack
		Message message1 = getMessage(groupId);
		MessageId messageId1 = message1.getId();
		db.addMessage(txn, message, DELIVERED, true, false, contactId);
		db.addMessage(txn, message1, DELIVERED, true, false, contactId);

		// Both message IDs should be returned
		Collection<MessageId> ids = db.getMessagesToAck(txn, contactId, 1234);
		assertEquals(asList(messageId, messageId1), ids);

		// Remove both message IDs
		db.lowerAckFlag(txn, contactId, asList(messageId, messageId1));

		// Both message IDs should have been removed
		assertEquals(emptyList(), db.getMessagesToAck(txn,
				contactId, 1234));

		// Raise the ack flag again
		db.raiseAckFlag(txn, contactId, messageId);
		db.raiseAckFlag(txn, contactId, messageId1);

		// Both message IDs should be returned
		ids = db.getMessagesToAck(txn, contactId, 1234);
		assertEquals(asList(messageId, messageId1), ids);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testOutstandingMessageAcked() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and a shared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// Retrieve the message from the database and mark it as sent
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		db.updateExpiryTimeAndEta(txn, contactId, messageId, MAX_LATENCY);

		// The message should no longer be sendable
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());

		// Pretend that the message was acked
		db.raiseSeenFlag(txn, contactId, messageId);

		// The message still should not be sendable
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testCloseWaitsForCommit() throws Exception {
		CountDownLatch closing = new CountDownLatch(1);
		CountDownLatch closed = new CountDownLatch(1);
		AtomicBoolean transactionFinished = new AtomicBoolean(false);
		AtomicBoolean error = new AtomicBoolean(false);
		Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread close = new Thread(() -> {
			try {
				closing.countDown();
				db.close();
				if (!transactionFinished.get()) error.set(true);
				closed.countDown();
			} catch (Exception e) {
				error.set(true);
			}
		});
		close.start();
		closing.await();
		// Do whatever the transaction needs to do
		Thread.sleep(10);
		transactionFinished.set(true);
		// Commit the transaction
		db.commitTransaction(txn);
		// The other thread should now terminate
		assertTrue(closed.await(5, SECONDS));
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testCloseWaitsForAbort() throws Exception {
		CountDownLatch closing = new CountDownLatch(1);
		CountDownLatch closed = new CountDownLatch(1);
		AtomicBoolean transactionFinished = new AtomicBoolean(false);
		AtomicBoolean error = new AtomicBoolean(false);
		Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread close = new Thread(() -> {
			try {
				closing.countDown();
				db.close();
				if (!transactionFinished.get()) error.set(true);
				closed.countDown();
			} catch (Exception e) {
				error.set(true);
			}
		});
		close.start();
		closing.await();
		// Do whatever the transaction needs to do
		Thread.sleep(10);
		transactionFinished.set(true);
		// Abort the transaction
		db.abortTransaction(txn);
		// The other thread should now terminate
		assertTrue(closed.await(5, SECONDS));
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testUpdateSettings() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Store some settings
		Settings s = new Settings();
		s.put("foo", "foo");
		s.put("bar", "bar");
		db.mergeSettings(txn, s, "test");
		assertEquals(s, db.getSettings(txn, "test"));

		// Update one of the settings and add another
		Settings s1 = new Settings();
		s1.put("bar", "baz");
		s1.put("bam", "bam");
		db.mergeSettings(txn, s1, "test");

		// Check that the settings were merged
		Settings merged = new Settings();
		merged.put("foo", "foo");
		merged.put("bar", "baz");
		merged.put("bam", "bam");
		assertEquals(merged, db.getSettings(txn, "test"));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContainsVisibleMessageRequiresMessageInDatabase()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and a shared group
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);

		// The message is not in the database
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContainsVisibleMessageRequiresGroupInDatabase()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));

		// The group is not in the database
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContainsVisibleMessageRequiresVisibileGroup()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, an invisible group and a message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// The group is not visible so the message should not be visible
		assertFalse(db.containsVisibleMessage(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGroupVisibility() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and a group
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);

		// The group should not be visible to the contact
		assertEquals(INVISIBLE, db.getGroupVisibility(txn, contactId, groupId));
		assertTrue(db.getGroupVisibility(txn, groupId).isEmpty());

		// Make the group visible to the contact
		db.addGroupVisibility(txn, contactId, groupId, false);
		assertEquals(VISIBLE, db.getGroupVisibility(txn, contactId, groupId));
		assertEquals(singletonMap(contactId, false),
				db.getGroupVisibility(txn, groupId));

		// Share the group with the contact
		db.setGroupVisibility(txn, contactId, groupId, true);
		assertEquals(SHARED, db.getGroupVisibility(txn, contactId, groupId));
		assertEquals(singletonMap(contactId, true),
				db.getGroupVisibility(txn, groupId));

		// Unshare the group with the contact
		db.setGroupVisibility(txn, contactId, groupId, false);
		assertEquals(VISIBLE, db.getGroupVisibility(txn, contactId, groupId));
		assertEquals(singletonMap(contactId, false),
				db.getGroupVisibility(txn, groupId));

		// Make the group invisible again
		db.removeGroupVisibility(txn, contactId, groupId);
		assertEquals(INVISIBLE, db.getGroupVisibility(txn, contactId, groupId));
		assertTrue(db.getGroupVisibility(txn, groupId).isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTransportKeys() throws Exception {
		long timePeriod = 123, timePeriod1 = 234;
		boolean active = random.nextBoolean();
		TransportKeys keys = createTransportKeys(timePeriod, active);
		TransportKeys keys1 = createTransportKeys(timePeriod1, active);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Initially there should be no transport keys in the database
		assertEquals(emptyList(), db.getTransportKeys(txn, transportId));

		// Add the contact, the transport and the transport keys
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addTransport(txn, transportId, 123);
		assertEquals(keySetId, db.addTransportKeys(txn, contactId, keys));
		assertEquals(keySetId1, db.addTransportKeys(txn, contactId, keys1));

		// Retrieve the transport keys
		Collection<TransportKeySet> allKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(2, allKeys.size());
		for (TransportKeySet ks : allKeys) {
			assertEquals(contactId, ks.getContactId());
			if (ks.getKeySetId().equals(keySetId)) {
				assertKeysEquals(keys, ks.getKeys());
			} else {
				assertEquals(keySetId1, ks.getKeySetId());
				assertKeysEquals(keys1, ks.getKeys());
			}
		}

		// Update the transport keys
		TransportKeys updated = createTransportKeys(timePeriod + 1, active);
		TransportKeys updated1 =
				createTransportKeys(timePeriod1 + 1, active);
		db.updateTransportKeys(txn, new TransportKeySet(keySetId, contactId,
				null, updated));
		db.updateTransportKeys(txn, new TransportKeySet(keySetId1, contactId,
				null, updated1));

		// Retrieve the transport keys again
		allKeys = db.getTransportKeys(txn, transportId);
		assertEquals(2, allKeys.size());
		for (TransportKeySet ks : allKeys) {
			assertEquals(contactId, ks.getContactId());
			if (ks.getKeySetId().equals(keySetId)) {
				assertKeysEquals(updated, ks.getKeys());
			} else {
				assertEquals(keySetId1, ks.getKeySetId());
				assertKeysEquals(updated1, ks.getKeys());
			}
		}

		// Removing the contact should remove the transport keys
		db.removeContact(txn, contactId);
		assertEquals(emptyList(), db.getTransportKeys(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	private void assertKeysEquals(TransportKeys expected,
			TransportKeys actual) {
		assertEquals(expected.getTransportId(), actual.getTransportId());
		assertEquals(expected.getTimePeriod(), actual.getTimePeriod());
		assertKeysEquals(expected.getPreviousIncomingKeys(),
				actual.getPreviousIncomingKeys());
		assertKeysEquals(expected.getCurrentIncomingKeys(),
				actual.getCurrentIncomingKeys());
		assertKeysEquals(expected.getNextIncomingKeys(),
				actual.getNextIncomingKeys());
		assertKeysEquals(expected.getCurrentOutgoingKeys(),
				actual.getCurrentOutgoingKeys());
		if (expected.isHandshakeMode()) {
			assertTrue(actual.isHandshakeMode());
			assertArrayEquals(expected.getRootKey().getBytes(),
					actual.getRootKey().getBytes());
			assertEquals(expected.isAlice(), actual.isAlice());
		} else {
			assertFalse(actual.isHandshakeMode());
		}
	}

	private void assertKeysEquals(IncomingKeys expected, IncomingKeys actual) {
		assertArrayEquals(expected.getTagKey().getBytes(),
				actual.getTagKey().getBytes());
		assertArrayEquals(expected.getHeaderKey().getBytes(),
				actual.getHeaderKey().getBytes());
		assertEquals(expected.getTimePeriod(), actual.getTimePeriod());
		assertEquals(expected.getWindowBase(), actual.getWindowBase());
		assertArrayEquals(expected.getWindowBitmap(), actual.getWindowBitmap());
	}

	private void assertKeysEquals(OutgoingKeys expected, OutgoingKeys actual) {
		assertArrayEquals(expected.getTagKey().getBytes(),
				actual.getTagKey().getBytes());
		assertArrayEquals(expected.getHeaderKey().getBytes(),
				actual.getHeaderKey().getBytes());
		assertEquals(expected.getTimePeriod(), actual.getTimePeriod());
		assertEquals(expected.getStreamCounter(), actual.getStreamCounter());
		assertEquals(expected.isActive(), actual.isActive());
	}

	@Test
	public void testHandshakeKeys() throws Exception {
		long timePeriod = 123, timePeriod1 = 234;
		boolean alice = random.nextBoolean();
		SecretKey rootKey = getSecretKey();
		SecretKey rootKey1 = getSecretKey();
		TransportKeys keys = createHandshakeKeys(timePeriod, rootKey, alice);
		TransportKeys keys1 = createHandshakeKeys(timePeriod1, rootKey1, alice);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Initially there should be no handshake keys in the database
		assertEquals(emptyList(), db.getTransportKeys(txn, transportId));

		// Add the contact, the transport and the handshake keys
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addTransport(txn, transportId, 123);
		assertEquals(keySetId, db.addTransportKeys(txn, contactId, keys));
		assertEquals(keySetId1, db.addTransportKeys(txn, contactId, keys1));

		// Retrieve the handshake keys
		Collection<TransportKeySet> allKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(2, allKeys.size());
		for (TransportKeySet ks : allKeys) {
			assertEquals(contactId, ks.getContactId());
			assertNull(ks.getPendingContactId());
			if (ks.getKeySetId().equals(keySetId)) {
				assertKeysEquals(keys, ks.getKeys());
			} else {
				assertEquals(keySetId1, ks.getKeySetId());
				assertKeysEquals(keys1, ks.getKeys());
			}
		}

		// Update the handshake keys
		TransportKeys updated =
				createHandshakeKeys(timePeriod + 1, rootKey, alice);
		TransportKeys updated1 =
				createHandshakeKeys(timePeriod1 + 1, rootKey1, alice);
		db.updateTransportKeys(txn, new TransportKeySet(keySetId, contactId,
				null, updated));
		db.updateTransportKeys(txn, new TransportKeySet(keySetId1, contactId,
				null, updated1));

		// Retrieve the handshake keys again
		allKeys = db.getTransportKeys(txn, transportId);
		assertEquals(2, allKeys.size());
		for (TransportKeySet ks : allKeys) {
			assertEquals(contactId, ks.getContactId());
			assertNull(ks.getPendingContactId());
			if (ks.getKeySetId().equals(keySetId)) {
				assertKeysEquals(updated, ks.getKeys());
			} else {
				assertEquals(keySetId1, ks.getKeySetId());
				assertKeysEquals(updated1, ks.getKeys());
			}
		}

		// Removing the contact should remove the handshake keys
		db.removeContact(txn, contactId);
		assertEquals(emptyList(), db.getTransportKeys(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testHandshakeKeysForPendingContact() throws Exception {
		long timePeriod = 123, timePeriod1 = 234;
		boolean alice = random.nextBoolean();
		SecretKey rootKey = getSecretKey();
		SecretKey rootKey1 = getSecretKey();
		TransportKeys keys = createHandshakeKeys(timePeriod, rootKey, alice);
		TransportKeys keys1 = createHandshakeKeys(timePeriod1, rootKey1, alice);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Initially there should be no handshake keys in the database
		assertEquals(emptyList(), db.getTransportKeys(txn, transportId));

		// Add the pending contact, the transport and the handshake keys
		db.addPendingContact(txn, pendingContact);
		db.addTransport(txn, transportId, 123);
		assertEquals(keySetId,
				db.addTransportKeys(txn, pendingContact.getId(), keys));
		assertEquals(keySetId1,
				db.addTransportKeys(txn, pendingContact.getId(), keys1));

		// Retrieve the handshake keys
		Collection<TransportKeySet> allKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(2, allKeys.size());
		for (TransportKeySet ks : allKeys) {
			assertNull(ks.getContactId());
			assertEquals(pendingContact.getId(), ks.getPendingContactId());
			if (ks.getKeySetId().equals(keySetId)) {
				assertKeysEquals(keys, ks.getKeys());
			} else {
				assertEquals(keySetId1, ks.getKeySetId());
				assertKeysEquals(keys1, ks.getKeys());
			}
		}

		// Update the handshake keys
		TransportKeys updated =
				createHandshakeKeys(timePeriod + 1, rootKey, alice);
		TransportKeys updated1 =
				createHandshakeKeys(timePeriod1 + 1, rootKey1, alice);
		db.updateTransportKeys(txn, new TransportKeySet(keySetId, null,
				pendingContact.getId(), updated));
		db.updateTransportKeys(txn, new TransportKeySet(keySetId1, null,
				pendingContact.getId(), updated1));

		// Retrieve the handshake keys again
		allKeys = db.getTransportKeys(txn, transportId);
		assertEquals(2, allKeys.size());
		for (TransportKeySet ks : allKeys) {
			assertNull(ks.getContactId());
			assertEquals(pendingContact.getId(), ks.getPendingContactId());
			if (ks.getKeySetId().equals(keySetId)) {
				assertKeysEquals(updated, ks.getKeys());
			} else {
				assertEquals(keySetId1, ks.getKeySetId());
				assertKeysEquals(updated1, ks.getKeys());
			}
		}

		// Removing the pending contact should remove the handshake keys
		db.removePendingContact(txn, pendingContact.getId());
		assertEquals(emptyList(), db.getTransportKeys(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testIncrementStreamCounter() throws Exception {
		long timePeriod = 123;
		TransportKeys keys = createTransportKeys(timePeriod, true);
		long streamCounter = keys.getCurrentOutgoingKeys().getStreamCounter();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact, transport and transport keys
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addTransport(txn, transportId, 123);
		assertEquals(keySetId, db.addTransportKeys(txn, contactId, keys));

		// Increment the stream counter twice and retrieve the transport keys
		db.incrementStreamCounter(txn, transportId, keySetId);
		db.incrementStreamCounter(txn, transportId, keySetId);
		Collection<TransportKeySet> newKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(1, newKeys.size());
		TransportKeySet ks = newKeys.iterator().next();
		assertEquals(keySetId, ks.getKeySetId());
		assertEquals(contactId, ks.getContactId());
		TransportKeys k = ks.getKeys();
		assertEquals(transportId, k.getTransportId());
		OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
		assertEquals(timePeriod, outCurr.getTimePeriod());
		assertEquals(streamCounter + 2, outCurr.getStreamCounter());

		// The rest of the keys should be unaffected
		assertKeysEquals(keys.getPreviousIncomingKeys(),
				k.getPreviousIncomingKeys());
		assertKeysEquals(keys.getCurrentIncomingKeys(),
				k.getCurrentIncomingKeys());
		assertKeysEquals(keys.getNextIncomingKeys(), k.getNextIncomingKeys());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testIncrementStreamCounterForHandshakeKeys() throws Exception {
		long timePeriod = 123;
		SecretKey rootKey = getSecretKey();
		boolean alice = random.nextBoolean();
		TransportKeys keys = createHandshakeKeys(timePeriod, rootKey, alice);
		long streamCounter = keys.getCurrentOutgoingKeys().getStreamCounter();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact, transport and handshake keys
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addTransport(txn, transportId, 123);
		assertEquals(keySetId, db.addTransportKeys(txn, contactId, keys));

		// Increment the stream counter twice and retrieve the handshake keys
		db.incrementStreamCounter(txn, transportId, keySetId);
		db.incrementStreamCounter(txn, transportId, keySetId);
		Collection<TransportKeySet> newKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(1, newKeys.size());
		TransportKeySet ks = newKeys.iterator().next();
		assertEquals(keySetId, ks.getKeySetId());
		assertEquals(contactId, ks.getContactId());
		TransportKeys k = ks.getKeys();
		assertEquals(transportId, k.getTransportId());
		assertNotNull(k.getRootKey());
		assertArrayEquals(rootKey.getBytes(), k.getRootKey().getBytes());
		assertEquals(alice, k.isAlice());
		OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
		assertEquals(timePeriod, outCurr.getTimePeriod());
		assertEquals(streamCounter + 2, outCurr.getStreamCounter());

		// The rest of the keys should be unaffected
		assertKeysEquals(keys.getPreviousIncomingKeys(),
				k.getPreviousIncomingKeys());
		assertKeysEquals(keys.getCurrentIncomingKeys(),
				k.getCurrentIncomingKeys());
		assertKeysEquals(keys.getNextIncomingKeys(), k.getNextIncomingKeys());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetReorderingWindow() throws Exception {
		boolean active = random.nextBoolean();
		long timePeriod = 123;
		TransportKeys keys = createTransportKeys(timePeriod, active);
		long base = keys.getCurrentIncomingKeys().getWindowBase();
		byte[] bitmap = keys.getCurrentIncomingKeys().getWindowBitmap();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact, transport and transport keys
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addTransport(txn, transportId, 123);
		assertEquals(keySetId, db.addTransportKeys(txn, contactId, keys));

		// Update the reordering window and retrieve the transport keys
		random.nextBytes(bitmap);
		db.setReorderingWindow(txn, keySetId, transportId, timePeriod,
				base + 1, bitmap);
		Collection<TransportKeySet> newKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(1, newKeys.size());
		TransportKeySet ks = newKeys.iterator().next();
		assertEquals(keySetId, ks.getKeySetId());
		assertEquals(contactId, ks.getContactId());
		TransportKeys k = ks.getKeys();
		assertEquals(transportId, k.getTransportId());
		IncomingKeys inCurr = k.getCurrentIncomingKeys();
		assertEquals(timePeriod, inCurr.getTimePeriod());
		assertEquals(base + 1, inCurr.getWindowBase());
		assertArrayEquals(bitmap, inCurr.getWindowBitmap());

		// The rest of the keys should be unaffected
		assertKeysEquals(keys.getPreviousIncomingKeys(),
				k.getPreviousIncomingKeys());
		assertKeysEquals(keys.getNextIncomingKeys(), k.getNextIncomingKeys());
		assertKeysEquals(keys.getCurrentOutgoingKeys(),
				k.getCurrentOutgoingKeys());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetReorderingWindowForHandshakeKeys() throws Exception {
		long timePeriod = 123;
		SecretKey rootKey = getSecretKey();
		boolean alice = random.nextBoolean();
		TransportKeys keys = createHandshakeKeys(timePeriod, rootKey, alice);
		long base = keys.getCurrentIncomingKeys().getWindowBase();
		byte[] bitmap = keys.getCurrentIncomingKeys().getWindowBitmap();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact, transport and handshake keys
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addTransport(txn, transportId, 123);
		assertEquals(keySetId, db.addTransportKeys(txn, contactId, keys));

		// Update the reordering window and retrieve the handshake keys
		random.nextBytes(bitmap);
		db.setReorderingWindow(txn, keySetId, transportId, timePeriod,
				base + 1, bitmap);
		Collection<TransportKeySet> newKeys =
				db.getTransportKeys(txn, transportId);
		assertEquals(1, newKeys.size());
		TransportKeySet ks = newKeys.iterator().next();
		assertEquals(keySetId, ks.getKeySetId());
		assertEquals(contactId, ks.getContactId());
		TransportKeys k = ks.getKeys();
		assertEquals(transportId, k.getTransportId());
		assertNotNull(k.getRootKey());
		assertArrayEquals(rootKey.getBytes(), k.getRootKey().getBytes());
		assertEquals(alice, k.isAlice());
		IncomingKeys inCurr = k.getCurrentIncomingKeys();
		assertEquals(timePeriod, inCurr.getTimePeriod());
		assertEquals(base + 1, inCurr.getWindowBase());
		assertArrayEquals(bitmap, inCurr.getWindowBitmap());

		// The rest of the keys should be unaffected
		assertKeysEquals(keys.getPreviousIncomingKeys(),
				k.getPreviousIncomingKeys());
		assertKeysEquals(keys.getNextIncomingKeys(), k.getNextIncomingKeys());
		assertKeysEquals(keys.getCurrentOutgoingKeys(),
				k.getCurrentOutgoingKeys());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetContactsByAuthorId() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add an identity for a local author - no contacts should be
		// associated
		db.addIdentity(txn, identity);

		// Add a contact associated with the local author
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));

		// Ensure contact is returned from database by author ID
		Collection<Contact> contacts =
				db.getContactsByAuthorId(txn, author.getId());
		assertEquals(1, contacts.size());
		assertEquals(contactId, contacts.iterator().next().getId());

		// Ensure no contacts are returned after contact was deleted
		db.removeContact(txn, contactId);
		contacts = db.getContactsByAuthorId(txn, author.getId());
		assertEquals(0, contacts.size());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetContactsByLocalAuthorId() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add an identity for a local author - no contacts should be
		// associated
		db.addIdentity(txn, identity);
		Collection<ContactId> contacts =
				db.getContacts(txn, localAuthor.getId());
		assertEquals(emptyList(), contacts);

		// Add a contact associated with the local author
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		contacts = db.getContacts(txn, localAuthor.getId());
		assertEquals(singletonList(contactId), contacts);

		// Remove the identity - the contact should be removed
		db.removeIdentity(txn, localAuthor.getId());
		contacts = db.getContacts(txn, localAuthor.getId());
		assertEquals(emptyList(), contacts);
		assertFalse(db.containsContact(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetContactsByHandshakePublicKey() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add an identity for a local author - no contacts should be
		// associated
		db.addIdentity(txn, identity);
		PublicKey handshakePublicKey = TestUtils.getSignaturePublicKey();
		Contact contact =
				db.getContact(txn, handshakePublicKey, localAuthor.getId());
		assertNull(contact);

		// Add a contact associated with the local author
		assertEquals(contactId, db.addContact(txn, author, localAuthor.getId(),
				handshakePublicKey, true));
		contact = db.getContact(txn, handshakePublicKey, localAuthor.getId());
		assertNotNull(contact);
		assertEquals(contactId, contact.getId());
		assertEquals(author, contact.getAuthor());
		assertNull(contact.getAlias());
		assertEquals(handshakePublicKey, contact.getHandshakePublicKey());
		assertTrue(contact.isVerified());
		assertEquals(author.getName(), contact.getAuthor().getName());
		assertEquals(author.getPublicKey(), contact.getAuthor().getPublicKey());
		assertEquals(author.getFormatVersion(),
				contact.getAuthor().getFormatVersion());

		// Ensure no contacts are returned after contact was deleted
		db.removeContact(txn, contactId);
		contact = db.getContact(txn, handshakePublicKey, localAuthor.getId());
		assertNull(contact);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testOfferedMessages() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact - initially there should be no offered messages
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		assertEquals(0, db.countOfferedMessages(txn, contactId));

		// Add some offered messages and count them
		List<MessageId> ids = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			MessageId m = new MessageId(getRandomId());
			db.addOfferedMessage(txn, contactId, m);
			ids.add(m);
		}
		assertEquals(10, db.countOfferedMessages(txn, contactId));

		// Remove some of the offered messages and count again
		List<MessageId> half = ids.subList(0, 5);
		db.removeOfferedMessages(txn, contactId, half);
		assertEquals(5, db.countOfferedMessages(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGroupMetadata() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group
		db.addGroup(txn, group);

		// Attach some metadata to the group
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[] {'b', 'a', 'r'});
		metadata.put("baz", new byte[] {'b', 'a', 'm'});
		db.mergeGroupMetadata(txn, groupId, metadata);

		// Retrieve the metadata for the group
		Metadata retrieved = db.getGroupMetadata(txn, groupId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Update the metadata
		metadata.put("foo", REMOVE);
		metadata.put("baz", new byte[] {'q', 'u', 'x'});
		db.mergeGroupMetadata(txn, groupId, metadata);

		// Retrieve the metadata again
		retrieved = db.getGroupMetadata(txn, groupId);
		assertEquals(1, retrieved.size());
		assertFalse(retrieved.containsKey("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessageMetadata() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// Attach some metadata to the message
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[] {'b', 'a', 'r'});
		metadata.put("baz", new byte[] {'b', 'a', 'm'});
		db.mergeMessageMetadata(txn, messageId, metadata);

		// Retrieve the metadata for the message
		Metadata retrieved = db.getMessageMetadata(txn, messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Retrieve the metadata for the group
		Map<MessageId, Metadata> all = db.getMessageMetadata(txn, groupId);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId));
		retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Update the metadata
		metadata.put("foo", REMOVE);
		metadata.put("baz", new byte[] {'q', 'u', 'x'});
		db.mergeMessageMetadata(txn, messageId, metadata);

		// Retrieve the metadata again
		retrieved = db.getMessageMetadata(txn, messageId);
		assertEquals(1, retrieved.size());
		assertFalse(retrieved.containsKey("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Retrieve the metadata for the group again
		all = db.getMessageMetadata(txn, groupId);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId));
		retrieved = all.get(messageId);
		assertEquals(1, retrieved.size());
		assertFalse(retrieved.containsKey("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Delete the metadata
		db.deleteMessageMetadata(txn, messageId);

		// Retrieve the metadata again
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());

		// Retrieve the metadata for the group again
		all = db.getMessageMetadata(txn, groupId);
		assertTrue(all.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessageMetadataOnlyForDeliveredMessages() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// Attach some metadata to the message
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[] {'b', 'a', 'r'});
		metadata.put("baz", new byte[] {'b', 'a', 'm'});
		db.mergeMessageMetadata(txn, messageId, metadata);

		// Retrieve the metadata for the message
		Metadata retrieved = db.getMessageMetadata(txn, messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));
		Map<MessageId, Metadata> map = db.getMessageMetadata(txn, groupId);
		assertEquals(1, map.size());
		assertTrue(map.get(messageId).containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), map.get(messageId).get("foo"));
		assertTrue(map.get(messageId).containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), map.get(messageId).get("baz"));

		// No metadata for unknown messages
		db.setMessageState(txn, messageId, UNKNOWN);
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());
		map = db.getMessageMetadata(txn, groupId);
		assertTrue(map.isEmpty());

		// No metadata for invalid messages
		db.setMessageState(txn, messageId, INVALID);
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());
		map = db.getMessageMetadata(txn, groupId);
		assertTrue(map.isEmpty());

		// No metadata for pending messages
		db.setMessageState(txn, messageId, PENDING);
		retrieved = db.getMessageMetadata(txn, messageId);
		assertTrue(retrieved.isEmpty());
		map = db.getMessageMetadata(txn, groupId);
		assertTrue(map.isEmpty());

		// Validator can get metadata for pending messages
		retrieved = db.getMessageMetadataForValidator(txn, messageId);
		assertFalse(retrieved.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMetadataQueries() throws Exception {
		Message message1 = getMessage(groupId);
		MessageId messageId1 = message1.getId();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and two messages
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true, false, null);
		db.addMessage(txn, message1, DELIVERED, true, false, null);

		// Attach some metadata to the messages
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[] {'b', 'a', 'r'});
		metadata.put("baz", new byte[] {'b', 'a', 'm'});
		db.mergeMessageMetadata(txn, messageId, metadata);
		Metadata metadata1 = new Metadata();
		metadata1.put("foo", new byte[] {'q', 'u', 'x'});
		db.mergeMessageMetadata(txn, messageId1, metadata1);

		// Retrieve all the metadata for the group
		Map<MessageId, Metadata> all = db.getMessageMetadata(txn, groupId);
		assertEquals(2, all.size());
		assertTrue(all.containsKey(messageId));
		assertTrue(all.containsKey(messageId1));
		Metadata retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));
		retrieved = all.get(messageId1);
		assertEquals(1, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata1.get("foo"), retrieved.get("foo"));

		// Query the metadata with an empty query
		Metadata query = new Metadata();
		all = db.getMessageMetadata(txn, groupId, query);
		assertEquals(2, all.size());
		assertTrue(all.containsKey(messageId));
		assertTrue(all.containsKey(messageId1));
		retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));
		retrieved = all.get(messageId1);
		assertEquals(1, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata1.get("foo"), retrieved.get("foo"));

		// Use a single-term query that matches the first message
		query = new Metadata();
		query.put("foo", metadata.get("foo"));
		all = db.getMessageMetadata(txn, groupId, query);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId));
		retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Use a single-term query that matches the second message
		query = new Metadata();
		query.put("foo", metadata1.get("foo"));
		all = db.getMessageMetadata(txn, groupId, query);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId1));
		retrieved = all.get(messageId1);
		assertEquals(1, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata1.get("foo"), retrieved.get("foo"));

		// Use a multi-term query that matches the first message
		query = new Metadata();
		query.put("foo", metadata.get("foo"));
		query.put("baz", metadata.get("baz"));
		all = db.getMessageMetadata(txn, groupId, query);
		assertEquals(1, all.size());
		assertTrue(all.containsKey(messageId));
		retrieved = all.get(messageId);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.containsKey("foo"));
		assertArrayEquals(metadata.get("foo"), retrieved.get("foo"));
		assertTrue(retrieved.containsKey("baz"));
		assertArrayEquals(metadata.get("baz"), retrieved.get("baz"));

		// Use a multi-term query that doesn't match any messages
		query = new Metadata();
		query.put("foo", metadata1.get("foo"));
		query.put("baz", metadata.get("baz"));
		all = db.getMessageMetadata(txn, groupId, query);
		assertTrue(all.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMetadataQueriesOnlyForDeliveredMessages() throws Exception {
		Message message1 = getMessage(groupId);
		MessageId messageId1 = message1.getId();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and two messages
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, true, false, null);
		db.addMessage(txn, message1, DELIVERED, true, false, null);

		// Attach some metadata to the messages
		Metadata metadata = new Metadata();
		metadata.put("foo", new byte[] {'b', 'a', 'r'});
		metadata.put("baz", new byte[] {'b', 'a', 'm'});
		db.mergeMessageMetadata(txn, messageId, metadata);
		Metadata metadata1 = new Metadata();
		metadata1.put("foo", new byte[] {'b', 'a', 'r'});
		db.mergeMessageMetadata(txn, messageId1, metadata1);

		for (int i = 0; i < 2; i++) {
			Metadata query;
			if (i == 0) {
				// Query the metadata with an empty query
				query = new Metadata();
			} else {
				// Query for foo
				query = new Metadata();
				query.put("foo", new byte[] {'b', 'a', 'r'});
			}

			db.setMessageState(txn, messageId, DELIVERED);
			db.setMessageState(txn, messageId1, DELIVERED);
			Map<MessageId, Metadata> all =
					db.getMessageMetadata(txn, groupId, query);
			assertEquals(2, all.size());
			assertMetadataEquals(metadata, all.get(messageId));
			assertMetadataEquals(metadata1, all.get(messageId1));

			// No metadata for unknown messages
			db.setMessageState(txn, messageId, UNKNOWN);
			all = db.getMessageMetadata(txn, groupId, query);
			assertEquals(1, all.size());
			assertMetadataEquals(metadata1, all.get(messageId1));

			// No metadata for invalid messages
			db.setMessageState(txn, messageId, INVALID);
			all = db.getMessageMetadata(txn, groupId, query);
			assertEquals(1, all.size());
			assertMetadataEquals(metadata1, all.get(messageId1));

			// No metadata for pending messages
			db.setMessageState(txn, messageId, PENDING);
			all = db.getMessageMetadata(txn, groupId, query);
			assertEquals(1, all.size());
			assertMetadataEquals(metadata1, all.get(messageId1));
		}

		db.commitTransaction(txn);
		db.close();
	}

	private void assertMetadataEquals(Metadata m1, Metadata m2) {
		assertEquals(m1.keySet(), m2.keySet());
		for (Entry<String, byte[]> e : m1.entrySet()) {
			assertArrayEquals(e.getValue(), m2.get(e.getKey()));
		}
	}

	@Test
	public void testMessageDependencies() throws Exception {
		Message message1 = getMessage(groupId);
		Message message2 = getMessage(groupId);
		Message message3 = getMessage(groupId);
		Message message4 = getMessage(groupId);
		MessageId messageId1 = message1.getId();
		MessageId messageId2 = message2.getId();
		MessageId messageId3 = message3.getId();
		MessageId messageId4 = message4.getId();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and some messages
		db.addGroup(txn, group);
		db.addMessage(txn, message, PENDING, true, false, contactId);
		db.addMessage(txn, message1, PENDING, true, false, contactId);
		db.addMessage(txn, message2, INVALID, true, false, contactId);

		// Add dependencies
		db.addMessageDependency(txn, message, messageId1, PENDING);
		db.addMessageDependency(txn, message, messageId2, PENDING);
		db.addMessageDependency(txn, message1, messageId3, PENDING);
		db.addMessageDependency(txn, message2, messageId4, INVALID);

		Map<MessageId, MessageState> dependencies;

		// Retrieve dependencies for root
		dependencies = db.getMessageDependencies(txn, messageId);
		assertEquals(2, dependencies.size());
		assertEquals(PENDING, dependencies.get(messageId1));
		assertEquals(INVALID, dependencies.get(messageId2));

		// Retrieve dependencies for message 1
		dependencies = db.getMessageDependencies(txn, messageId1);
		assertEquals(1, dependencies.size());
		assertEquals(UNKNOWN, dependencies.get(messageId3)); // Missing

		// Retrieve dependencies for message 2
		dependencies = db.getMessageDependencies(txn, messageId2);
		assertEquals(1, dependencies.size());
		assertEquals(UNKNOWN, dependencies.get(messageId4)); // Missing

		// Make sure leaves have no dependencies
		dependencies = db.getMessageDependencies(txn, messageId3);
		assertEquals(0, dependencies.size());
		dependencies = db.getMessageDependencies(txn, messageId4);
		assertEquals(0, dependencies.size());

		Map<MessageId, MessageState> dependents;

		// Root message does not have dependents
		dependents = db.getMessageDependents(txn, messageId);
		assertEquals(0, dependents.size());

		// Messages 1 and 2 have the root as a dependent
		dependents = db.getMessageDependents(txn, messageId1);
		assertEquals(1, dependents.size());
		assertEquals(PENDING, dependents.get(messageId));
		dependents = db.getMessageDependents(txn, messageId2);
		assertEquals(1, dependents.size());
		assertEquals(PENDING, dependents.get(messageId));

		// Message 3 is missing, so it has no dependents
		dependents = db.getMessageDependents(txn, messageId3);
		assertEquals(0, dependents.size());

		// Add message 3
		db.addMessage(txn, message3, UNKNOWN, false, false, contactId);

		// Message 3 has message 1 as a dependent
		dependents = db.getMessageDependents(txn, messageId3);
		assertEquals(1, dependents.size());
		assertEquals(PENDING, dependents.get(messageId1));

		// Message 4 is missing, so it has no dependents
		dependents = db.getMessageDependents(txn, messageId4);
		assertEquals(0, dependents.size());

		// Add message 4
		db.addMessage(txn, message4, UNKNOWN, false, false, contactId);

		// Message 4 has message 2 as a dependent
		dependents = db.getMessageDependents(txn, messageId4);
		assertEquals(1, dependents.size());
		assertEquals(INVALID, dependents.get(messageId2));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessageDependenciesAcrossGroups() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, PENDING, true, false, contactId);

		// Add a second group
		Group group1 = getGroup(clientId, 123);
		GroupId groupId1 = group1.getId();
		db.addGroup(txn, group1);

		// Add a message to the second group
		Message message1 = getMessage(groupId1);
		MessageId messageId1 = message1.getId();
		db.addMessage(txn, message1, DELIVERED, true, false, contactId);

		// Create an ID for a missing message
		MessageId messageId2 = new MessageId(getRandomId());

		// Add another message to the first group
		Message message3 = getMessage(groupId);
		MessageId messageId3 = message3.getId();
		db.addMessage(txn, message3, DELIVERED, true, false, contactId);

		// Add dependencies between the messages
		db.addMessageDependency(txn, message, messageId1, PENDING);
		db.addMessageDependency(txn, message, messageId2, PENDING);
		db.addMessageDependency(txn, message, messageId3, PENDING);

		// Retrieve the dependencies for the root
		Map<MessageId, MessageState> dependencies;
		dependencies = db.getMessageDependencies(txn, messageId);

		// The cross-group dependency should have state UNKNOWN
		assertEquals(UNKNOWN, dependencies.get(messageId1));

		// The missing dependency should have state UNKNOWN
		assertEquals(UNKNOWN, dependencies.get(messageId2));

		// The valid dependency should have its real state
		assertEquals(DELIVERED, dependencies.get(messageId3));

		// Retrieve the dependents for the message in the second group
		Map<MessageId, MessageState> dependents;
		dependents = db.getMessageDependents(txn, messageId1);

		// The cross-group dependent should be excluded
		assertFalse(dependents.containsKey(messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetPendingMessagesForDelivery() throws Exception {
		Message message1 = getMessage(groupId);
		Message message2 = getMessage(groupId);
		Message message3 = getMessage(groupId);
		Message message4 = getMessage(groupId);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and some messages with different states
		db.addGroup(txn, group);
		db.addMessage(txn, message1, UNKNOWN, true, false, contactId);
		db.addMessage(txn, message2, INVALID, true, false, contactId);
		db.addMessage(txn, message3, PENDING, true, false, contactId);
		db.addMessage(txn, message4, DELIVERED, true, false, contactId);

		Collection<MessageId> result;

		// Retrieve messages to be validated
		result = db.getMessagesToValidate(txn);
		assertEquals(1, result.size());
		assertTrue(result.contains(message1.getId()));

		// Retrieve pending messages
		result = db.getPendingMessages(txn);
		assertEquals(1, result.size());
		assertTrue(result.contains(message3.getId()));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessagesToShare() throws Exception {
		Message message1 = getMessage(groupId);
		Message message2 = getMessage(groupId);
		Message message3 = getMessage(groupId);
		Message message4 = getMessage(groupId);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and some messages
		db.addGroup(txn, group);
		db.addMessage(txn, message1, DELIVERED, true, false, contactId);
		db.addMessage(txn, message2, DELIVERED, false, false, contactId);
		db.addMessage(txn, message3, DELIVERED, false, false, contactId);
		db.addMessage(txn, message4, DELIVERED, true, false, contactId);

		// Introduce dependencies between the messages
		db.addMessageDependency(txn, message1, message2.getId(), DELIVERED);
		db.addMessageDependency(txn, message3, message1.getId(), DELIVERED);
		db.addMessageDependency(txn, message4, message3.getId(), DELIVERED);

		// Retrieve messages to be shared
		Collection<MessageId> result = db.getMessagesToShare(txn);
		assertEquals(2, result.size());
		assertTrue(result.contains(message2.getId()));
		assertTrue(result.contains(message3.getId()));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageStatus() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and a shared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// The message should not be sent or seen
		MessageStatus status = db.getMessageStatus(txn, contactId, messageId);
		assertNotNull(status);
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertFalse(status.isSent());
		assertFalse(status.isSeen());

		// The same status should be returned when querying by group
		Collection<MessageStatus> statuses = db.getMessageStatus(txn,
				contactId, groupId);
		assertEquals(1, statuses.size());
		status = statuses.iterator().next();
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertFalse(status.isSent());
		assertFalse(status.isSeen());

		// Pretend the message was sent to the contact
		db.updateExpiryTimeAndEta(txn, contactId, messageId, Integer.MAX_VALUE);

		// The message should be sent but not seen
		status = db.getMessageStatus(txn, contactId, messageId);
		assertNotNull(status);
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertTrue(status.isSent());
		assertFalse(status.isSeen());

		// The same status should be returned when querying by group
		statuses = db.getMessageStatus(txn, contactId, groupId);
		assertEquals(1, statuses.size());
		status = statuses.iterator().next();
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertTrue(status.isSent());
		assertFalse(status.isSeen());

		// Pretend the message was acked by the contact
		db.raiseSeenFlag(txn, contactId, messageId);

		// The message should be sent and seen
		status = db.getMessageStatus(txn, contactId, messageId);
		assertNotNull(status);
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertTrue(status.isSent());
		assertTrue(status.isSeen());

		// The same status should be returned when querying by group
		statuses = db.getMessageStatus(txn, contactId, groupId);
		assertEquals(1, statuses.size());
		status = statuses.iterator().next();
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertTrue(status.isSent());
		assertTrue(status.isSeen());

		// Make the group invisible to the contact
		db.removeGroupVisibility(txn, contactId, groupId);

		// Null should be returned when querying by message
		assertNull(db.getMessageStatus(txn, contactId, messageId));

		// No statuses should be returned when querying by group
		statuses = db.getMessageStatus(txn, contactId, groupId);
		assertEquals(0, statuses.size());

		// Make the group visible to the contact again
		db.addGroupVisibility(txn, contactId, groupId, false);

		// The default status should be returned when querying by message
		status = db.getMessageStatus(txn, contactId, messageId);
		assertNotNull(status);
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertFalse(status.isSent());
		assertFalse(status.isSeen());

		// The default status should be returned when querying by group
		statuses = db.getMessageStatus(txn, contactId, groupId);
		assertEquals(1, statuses.size());
		status = statuses.iterator().next();
		assertEquals(messageId, status.getMessageId());
		assertEquals(contactId, status.getContactId());
		assertFalse(status.isSent());
		assertFalse(status.isSeen());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testDifferentLocalAuthorsCanHaveTheSameContact()
			throws Exception {
		Identity identity1 = getIdentity();
		LocalAuthor localAuthor1 = identity1.getLocalAuthor();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add identities for two local authors
		db.addIdentity(txn, identity);
		db.addIdentity(txn, identity1);

		// Add the same contact for each local author
		ContactId contactId =
				db.addContact(txn, author, localAuthor.getId(), null, true);
		ContactId contactId1 =
				db.addContact(txn, author, localAuthor1.getId(), null, true);

		// The contacts should be distinct
		assertNotEquals(contactId, contactId1);
		assertEquals(2, db.getContacts(txn).size());
		assertEquals(1, db.getContacts(txn, localAuthor.getId()).size());
		assertEquals(1, db.getContacts(txn, localAuthor1.getId()).size());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testDeleteMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and a shared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// The message should be visible to the contact
		assertTrue(db.containsVisibleMessage(txn, contactId, messageId));

		// The message should be sendable
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);

		// The message should be available
		Message m = db.getMessage(txn, messageId);
		assertEquals(messageId, m.getId());
		assertEquals(groupId, m.getGroupId());
		assertEquals(message.getTimestamp(), m.getTimestamp());
		assertArrayEquals(message.getBody(), m.getBody());

		// Delete the message
		db.deleteMessage(txn, messageId);

		// The message should be visible to the contact
		assertTrue(db.containsVisibleMessage(txn, contactId, messageId));

		// The message should not be sendable
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());
		ids = db.getMessagesToOffer(txn, contactId, 100, MAX_LATENCY);
		assertTrue(ids.isEmpty());

		// Requesting the message should throw an exception
		try {
			db.getMessage(txn, messageId);
			fail();
		} catch (MessageDeletedException expected) {
			// Expected
		}

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetContactAlias() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));

		// The contact should have no alias
		Contact contact = db.getContact(txn, contactId);
		assertNull(contact.getAlias());

		// Set a contact alias
		String alias = getRandomString(MAX_AUTHOR_NAME_LENGTH);
		db.setContactAlias(txn, contactId, alias);

		// The contact should have an alias
		contact = db.getContact(txn, contactId);
		assertEquals(alias, contact.getAlias());

		// Set the contact alias null
		db.setContactAlias(txn, contactId, null);

		// The contact should have no alias
		contact = db.getContact(txn, contactId);
		assertNull(contact.getAlias());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetMessageState() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, UNKNOWN, false, false, contactId);

		// Walk the message through the validation and delivery states
		assertEquals(UNKNOWN, db.getMessageState(txn, messageId));
		db.setMessageState(txn, messageId, INVALID);
		assertEquals(INVALID, db.getMessageState(txn, messageId));
		db.setMessageState(txn, messageId, PENDING);
		assertEquals(PENDING, db.getMessageState(txn, messageId));
		db.setMessageState(txn, messageId, DELIVERED);
		assertEquals(DELIVERED, db.getMessageState(txn, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetNextSendTime() throws Exception {
		long now = System.currentTimeMillis();
		Database<Connection> db = open(false, new TestMessageFactory(),
				new StoppedClock(now));
		Connection txn = db.startTransaction();

		// Add a contact, a group and a message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addMessage(txn, message, UNKNOWN, false, false, null);

		// There should be no messages to send
		assertEquals(Long.MAX_VALUE, db.getNextSendTime(txn, contactId));

		// Share the group with the contact - still no messages to send
		db.addGroupVisibility(txn, contactId, groupId, true);
		assertEquals(Long.MAX_VALUE, db.getNextSendTime(txn, contactId));

		// Set the message's state to DELIVERED - still no messages to send
		db.setMessageState(txn, messageId, DELIVERED);
		assertEquals(Long.MAX_VALUE, db.getNextSendTime(txn, contactId));

		// Share the message - now it should be sendable immediately
		db.setMessageShared(txn, messageId, true);
		assertEquals(0, db.getNextSendTime(txn, contactId));

		// Mark the message as requested - it should still be sendable
		db.raiseRequestedFlag(txn, contactId, messageId);
		assertEquals(0, db.getNextSendTime(txn, contactId));

		// Update the message's expiry time as though we sent it - now the
		// message should be sendable after one round-trip
		db.updateExpiryTimeAndEta(txn, contactId, messageId, 1000);
		assertEquals(now + 2000, db.getNextSendTime(txn, contactId));

		// Update the message's expiry time again - now it should be sendable
		// after two round-trips
		db.updateExpiryTimeAndEta(txn, contactId, messageId, 1000);
		assertEquals(now + 4000, db.getNextSendTime(txn, contactId));

		// Delete the message - there should be no messages to send
		db.deleteMessage(txn, messageId);
		assertEquals(Long.MAX_VALUE, db.getNextSendTime(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroups() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		assertEquals(emptyList(), db.getGroups(txn, clientId, majorVersion));
		db.addGroup(txn, group);
		assertEquals(singletonList(group),
				db.getGroups(txn, clientId, majorVersion));
		db.removeGroup(txn, groupId);
		assertEquals(emptyList(), db.getGroups(txn, clientId, majorVersion));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testExceptionHandling() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();
		try {
			// Ask for a nonexistent message - an exception should be thrown
			db.getMessage(txn, messageId);
			fail();
		} catch (DbException expected) {
			// It should be possible to abort the transaction without error
			db.abortTransaction(txn);
		}
		// It should be possible to close the database cleanly
		db.close();
	}

	@Test
	public void testMessageRetransmission() throws Exception {
		long now = System.currentTimeMillis();
		AtomicLong time = new AtomicLong(now);
		Database<Connection> db =
				open(false, new TestMessageFactory(), new SettableClock(time));
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and a shared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// Time: now
		// Retrieve the message from the database
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);

		// Time: now
		// Mark the message as sent
		db.updateExpiryTimeAndEta(txn, contactId, messageId, MAX_LATENCY);

		// The message should expire after 2 * MAX_LATENCY
		assertEquals(now + MAX_LATENCY * 2, db.getNextSendTime(txn, contactId));

		// Time: now + MAX_LATENCY * 2 - 1
		// The message should not yet be sendable
		time.set(now + MAX_LATENCY * 2 - 1);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());

		// Time: now + MAX_LATENCY * 2
		// The message should have expired and should now be sendable
		time.set(now + MAX_LATENCY * 2);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testFasterMessageRetransmission() throws Exception {
		long now = System.currentTimeMillis();
		AtomicLong time = new AtomicLong(now);
		Database<Connection> db =
				open(false, new TestMessageFactory(), new SettableClock(time));
		Connection txn = db.startTransaction();

		// Add a contact, a shared group and a shared message
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));
		db.addGroup(txn, group);
		db.addGroupVisibility(txn, contactId, groupId, true);
		db.addMessage(txn, message, DELIVERED, true, false, null);

		// Time: now
		// Retrieve the message from the database
		Collection<MessageId> ids = db.getMessagesToSend(txn, contactId,
				ONE_MEGABYTE, MAX_LATENCY);
		assertEquals(singletonList(messageId), ids);

		// Time: now
		// Mark the message as sent
		db.updateExpiryTimeAndEta(txn, contactId, messageId, MAX_LATENCY);

		// The message should expire after 2 * MAX_LATENCY
		assertEquals(now + MAX_LATENCY * 2, db.getNextSendTime(txn, contactId));

		// Time: now
		// The message should not be sendable via the same transport
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE, MAX_LATENCY);
		assertTrue(ids.isEmpty());

		// Time: now
		// The message should be sendable via a transport with a faster ETA
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE,
				MAX_LATENCY - 1);
		assertEquals(singletonList(messageId), ids);

		// Time: now + 1
		// The message should no longer be sendable via the faster transport,
		// as the ETA is now equal
		time.set(now + 1);
		ids = db.getMessagesToSend(txn, contactId, ONE_MEGABYTE,
				MAX_LATENCY - 1);
		assertTrue(ids.isEmpty());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testCompactionTime() throws Exception {
		MessageFactory messageFactory = new TestMessageFactory();
		long now = System.currentTimeMillis();
		AtomicLong time = new AtomicLong(now);
		Clock clock = new SettableClock(time);

		// Time: now
		// The last compaction time should be initialised to the current time
		Database<Connection> db = open(false, messageFactory, clock);
		Connection txn = db.startTransaction();
		Settings s = db.getSettings(txn, DB_SETTINGS_NAMESPACE);
		assertEquals(now, s.getLong(LAST_COMPACTED_KEY, 0));
		db.commitTransaction(txn);
		db.close();

		// Time: now + MAX_COMPACTION_INTERVAL_MS
		// The DB should not be compacted, so the last compaction time should
		// not be updated
		time.set(now + MAX_COMPACTION_INTERVAL_MS);
		db = open(true, messageFactory, clock);
		txn = db.startTransaction();
		s = db.getSettings(txn, DB_SETTINGS_NAMESPACE);
		assertEquals(now, s.getLong(LAST_COMPACTED_KEY, 0));
		db.commitTransaction(txn);
		db.close();

		// Time: now + MAX_COMPACTION_INTERVAL_MS + 1
		// The DB should be compacted, so the last compaction time should be
		// updated
		time.set(now + MAX_COMPACTION_INTERVAL_MS + 1);
		db = open(true, messageFactory, clock);
		txn = db.startTransaction();
		s = db.getSettings(txn, DB_SETTINGS_NAMESPACE);
		assertEquals(now + MAX_COMPACTION_INTERVAL_MS + 1,
				s.getLong(LAST_COMPACTED_KEY, 0));
		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testPendingContacts() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		assertEquals(emptyList(), db.getPendingContacts(txn));

		db.addPendingContact(txn, pendingContact);
		PendingContact retrieved =
				db.getPendingContact(txn, pendingContact.getId());
		assertPendingContactEquals(pendingContact, retrieved);

		Collection<PendingContact> pendingContacts = db.getPendingContacts(txn);
		assertEquals(1, pendingContacts.size());
		retrieved = pendingContacts.iterator().next();
		assertPendingContactEquals(pendingContact, retrieved);

		db.removePendingContact(txn, pendingContact.getId());
		assertEquals(emptyList(), db.getPendingContacts(txn));

		db.commitTransaction(txn);
		db.close();
	}

	private void assertPendingContactEquals(PendingContact expected,
			PendingContact actual) {
		assertEquals(expected.getId(), actual.getId());
		assertArrayEquals(expected.getPublicKey().getEncoded(),
				actual.getPublicKey().getEncoded());
		assertEquals(expected.getAlias(), actual.getAlias());
		assertEquals(expected.getTimestamp(), actual.getTimestamp());
	}

	@Test
	public void testSetHandshakeKeyPair() throws Exception {
		Identity withoutKeys = new Identity(localAuthor, null, null,
				identity.getTimeCreated());
		assertFalse(withoutKeys.hasHandshakeKeyPair());
		PublicKey publicKey = getAgreementPublicKey();
		PrivateKey privateKey = getAgreementPrivateKey();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		db.addIdentity(txn, withoutKeys);
		Identity retrieved = db.getIdentity(txn, localAuthor.getId());
		assertFalse(retrieved.hasHandshakeKeyPair());
		db.setHandshakeKeyPair(txn, localAuthor.getId(), publicKey, privateKey);
		retrieved = db.getIdentity(txn, localAuthor.getId());
		assertTrue(retrieved.hasHandshakeKeyPair());
		PublicKey handshakePub = retrieved.getHandshakePublicKey();
		assertNotNull(handshakePub);
		assertArrayEquals(publicKey.getEncoded(), handshakePub.getEncoded());
		PrivateKey handshakePriv = retrieved.getHandshakePrivateKey();
		assertNotNull(handshakePriv);
		assertArrayEquals(privateKey.getEncoded(), handshakePriv.getEncoded());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTemporaryMessages() throws Exception {
		Message message1 = getMessage(groupId);
		MessageId messageId1 = message1.getId();

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a group and two temporary messages
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, false, true, null);
		db.addMessage(txn, message1, DELIVERED, false, true, null);

		// Mark one of the messages as permanent
		db.setMessagePermanent(txn, messageId);

		// Remove all temporary messages
		db.removeTemporaryMessages(txn);

		// The permanent message should not have been removed
		assertTrue(db.containsMessage(txn, messageId));

		// The temporary message should have been removed
		assertFalse(db.containsMessage(txn, messageId1));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSyncVersions() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addIdentity(txn, identity);
		assertEquals(contactId,
				db.addContact(txn, author, localAuthor.getId(), null, true));

		// Only sync version 0 should be supported by default
		List<Byte> defaultSupported = singletonList((byte) 0);
		assertEquals(defaultSupported, db.getSyncVersions(txn, contactId));

		// Set the supported versions and check that they're returned
		List<Byte> supported = asList((byte) 0, (byte) 1);
		db.setSyncVersions(txn, contactId, supported);
		assertEquals(supported, db.getSyncVersions(txn, contactId));

		db.commitTransaction(txn);
		db.close();
	}


	@Test
	public void testShutdownGracefully() throws Exception {
		Database<Connection> db = open(false);
		db.close();
		open(true);
		assertFalse(db.wasDirtyOnInitialisation());
	}

	@Test
	public void testShutdownDirty() throws Exception {
		Database<Connection> db = open(false);

		// We want to simulate a dirty shutdown here which would normally be
		// caused by an empty battery or by force closing the Android app.
		// As there is no obvious way to simulate this, we're artificially
		// causing an SqlException during close() here by unloading the JDBC
		// drivers.
		List<String> unloadedDrivers = unloadDrivers();

		try {
			db.close();
			fail();
		} catch (Exception e) {
			// continue
			e.printStackTrace();
		}

		// Reloading drivers to continue so that we're able to work with the
		// database again.
		reloadDrivers(unloadedDrivers);

		db = open(true);
		assertTrue(db.wasDirtyOnInitialisation());
	}

	@Test
	public void testShutdownDirtyThenGracefully() throws Exception {
		Database<Connection> db = open(false);

		// Simulating a dirty shutdown here, look at #testShutdownDirty for
		// details.
		List<String> unloadedDrivers = unloadDrivers();

		try {
			db.close();
			fail();
		} catch (Exception e) {
			// continue
		}

		reloadDrivers(unloadedDrivers);

		db = open(true);
		assertTrue(db.wasDirtyOnInitialisation());

		db.close();
		db = open(true);
		assertFalse(db.wasDirtyOnInitialisation());
	}

	@Test
	public void testCleanupTimer() throws Exception {
		long duration = 60_000;
		long now = System.currentTimeMillis();
		AtomicLong time = new AtomicLong(now);
		Database<Connection> db =
				open(false, new TestMessageFactory(), new SettableClock(time));
		Connection txn = db.startTransaction();

		// No messages should be due or scheduled for deletion
		assertTrue(db.getMessagesToDelete(txn).isEmpty());
		assertEquals(NO_CLEANUP_DEADLINE, db.getNextCleanupDeadline(txn));

		// Add a group and a message
		db.addGroup(txn, group);
		db.addMessage(txn, message, DELIVERED, false, false, null);

		// No messages should be due or scheduled for deletion
		assertTrue(db.getMessagesToDelete(txn).isEmpty());
		assertEquals(NO_CLEANUP_DEADLINE, db.getNextCleanupDeadline(txn));

		// Set the message's cleanup timer duration
		db.setCleanupTimerDuration(txn, messageId, duration);

		// No messages should be due or scheduled for deletion
		assertTrue(db.getMessagesToDelete(txn).isEmpty());
		assertEquals(NO_CLEANUP_DEADLINE, db.getNextCleanupDeadline(txn));

		// Start the message's cleanup timer
		assertEquals(now + duration, db.startCleanupTimer(txn, messageId));

		// The timer can't be started again
		assertEquals(TIMER_NOT_STARTED, db.startCleanupTimer(txn, messageId));

		// No messages should be due for deletion, but the message should be
		// scheduled for deletion
		assertTrue(db.getMessagesToDelete(txn).isEmpty());
		assertEquals(now + duration, db.getNextCleanupDeadline(txn));

		// Stop the timer
		db.stopCleanupTimer(txn, messageId);

		// No messages should be due or scheduled for deletion
		assertTrue(db.getMessagesToDelete(txn).isEmpty());
		assertEquals(NO_CLEANUP_DEADLINE, db.getNextCleanupDeadline(txn));

		// Start the timer again
		assertEquals(now + duration, db.startCleanupTimer(txn, messageId));

		// No messages should be due for deletion, but the message should be
		// scheduled for deletion
		assertTrue(db.getMessagesToDelete(txn).isEmpty());
		assertEquals(now + duration, db.getNextCleanupDeadline(txn));

		// 1 ms before the timer expires, no messages should be due for
		// deletion but the message should be scheduled for deletion
		time.set(now + duration - 1);
		assertTrue(db.getMessagesToDelete(txn).isEmpty());
		assertEquals(now + duration, db.getNextCleanupDeadline(txn));

		// When the timer expires, the message should be due and scheduled for
		// deletion
		time.set(now + duration);
		assertEquals(singletonMap(groupId, singletonList(messageId)),
				db.getMessagesToDelete(txn));
		assertEquals(now + duration, db.getNextCleanupDeadline(txn));

		// 1 ms after the timer expires, the message should be due and
		// scheduled for deletion
		time.set(now + duration + 1);
		assertEquals(singletonMap(groupId, singletonList(messageId)),
				db.getMessagesToDelete(txn));
		assertEquals(now + duration, db.getNextCleanupDeadline(txn));

		// Once the message has been deleted, it should no longer be due
		// or scheduled for deletion
		db.deleteMessage(txn, messageId);
		assertTrue(db.getMessagesToDelete(txn).isEmpty());
		assertEquals(NO_CLEANUP_DEADLINE, db.getNextCleanupDeadline(txn));
	}

	private Database<Connection> open(boolean resume) throws Exception {
		return open(resume, new TestMessageFactory(), new SystemClock());
	}

	private Database<Connection> open(boolean resume,
			MessageFactory messageFactory, Clock clock) throws Exception {
		Database<Connection> db = createDatabase(
				new TestDatabaseConfig(testDir), messageFactory, clock);
		if (!resume) deleteTestDirectory(testDir);
		db.open(key, null);
		return db;
	}

	private TransportKeys createTransportKeys(long timePeriod, boolean active) {
		SecretKey inPrevTagKey = getSecretKey();
		SecretKey inPrevHeaderKey = getSecretKey();
		IncomingKeys inPrev = new IncomingKeys(inPrevTagKey, inPrevHeaderKey,
				timePeriod - 1, 123, new byte[4]);
		SecretKey inCurrTagKey = getSecretKey();
		SecretKey inCurrHeaderKey = getSecretKey();
		IncomingKeys inCurr = new IncomingKeys(inCurrTagKey, inCurrHeaderKey,
				timePeriod, 234, new byte[4]);
		SecretKey inNextTagKey = getSecretKey();
		SecretKey inNextHeaderKey = getSecretKey();
		IncomingKeys inNext = new IncomingKeys(inNextTagKey, inNextHeaderKey,
				timePeriod + 1, 345, new byte[4]);
		SecretKey outCurrTagKey = getSecretKey();
		SecretKey outCurrHeaderKey = getSecretKey();
		OutgoingKeys outCurr = new OutgoingKeys(outCurrTagKey, outCurrHeaderKey,
				timePeriod, 456, active);
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr);
	}

	private TransportKeys createHandshakeKeys(long timePeriod,
			SecretKey rootKey, boolean alice) {
		SecretKey inPrevTagKey = getSecretKey();
		SecretKey inPrevHeaderKey = getSecretKey();
		IncomingKeys inPrev = new IncomingKeys(inPrevTagKey, inPrevHeaderKey,
				timePeriod - 1, 123, new byte[4]);
		SecretKey inCurrTagKey = getSecretKey();
		SecretKey inCurrHeaderKey = getSecretKey();
		IncomingKeys inCurr = new IncomingKeys(inCurrTagKey, inCurrHeaderKey,
				timePeriod, 234, new byte[4]);
		SecretKey inNextTagKey = getSecretKey();
		SecretKey inNextHeaderKey = getSecretKey();
		IncomingKeys inNext = new IncomingKeys(inNextTagKey, inNextHeaderKey,
				timePeriod + 1, 345, new byte[4]);
		SecretKey outCurrTagKey = getSecretKey();
		SecretKey outCurrHeaderKey = getSecretKey();
		OutgoingKeys outCurr = new OutgoingKeys(outCurrTagKey, outCurrHeaderKey,
				timePeriod, 456, true);
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr,
				rootKey, alice);
	}

	private List<String> unloadDrivers() {
		Enumeration<Driver> drivers = DriverManager.getDrivers();
		List<String> unloaded = new ArrayList<>();
		while (drivers.hasMoreElements()) {
			Driver d = drivers.nextElement();
			try {
				DriverManager.deregisterDriver(d);
				unloaded.add(d.getClass().getName());
			} catch (SQLException e) {
				e.printStackTrace();
				fail();
			}
		}
		return unloaded;
	}

	private void reloadDrivers(List<String> unloadedDrivers)
			throws ClassNotFoundException, IllegalAccessException,
			InstantiationException, SQLException {
		for (String driverName : unloadedDrivers) {
			DriverManager.registerDriver(
					(Driver) Class.forName(driverName).newInstance());
		}
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}

	private static class StoppedClock implements Clock {

		private final long time;

		private StoppedClock(long time) {
			this.time = time;
		}

		@Override
		public long currentTimeMillis() {
			return time;
		}

		@Override
		public void sleep(long milliseconds) throws InterruptedException {
			Thread.sleep(milliseconds);
		}
	}
}
