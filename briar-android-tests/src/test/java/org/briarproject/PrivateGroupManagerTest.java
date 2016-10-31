package org.briarproject;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageStateChangedEvent;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.JoinMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.privategroup.PrivateGroupModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.TestPluginsModule.MAX_LATENCY;
import static org.briarproject.api.identity.Author.Status.VERIFIED;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.api.sync.ValidationManager.State.PENDING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrivateGroupManagerTest extends BriarIntegrationTest {

	private LifecycleManager lifecycleManager0, lifecycleManager1;
	private SyncSessionFactory sync0, sync1;
	private PrivateGroupManager groupManager0, groupManager1;
	private ContactManager contactManager0, contactManager1;
	private ContactId contactId0, contactId1;
	private IdentityManager identityManager0, identityManager1;
	private LocalAuthor author0, author1;
	private PrivateGroup privateGroup0;
	private GroupId groupId0;
	private GroupMessage newMemberMsg0;

	@Inject
	Clock clock;
	@Inject
	AuthorFactory authorFactory;
	@Inject
	CryptoComponent crypto;
	@Inject
	PrivateGroupFactory privateGroupFactory;
	@Inject
	GroupMessageFactory groupMessageFactory;

	// objects accessed from background threads need to be volatile
	private volatile Waiter validationWaiter;
	private volatile Waiter deliveryWaiter;

	private final File testDir = TestUtils.getTestDirectory();
	private final SecretKey master = TestUtils.getSecretKey();
	private final int TIMEOUT = 15000;
	private final String AUTHOR1 = "Author 1";
	private final String AUTHOR2 = "Author 2";

	private static final Logger LOG =
			Logger.getLogger(PrivateGroupManagerTest.class.getName());

	private PrivateGroupManagerTestComponent t0, t1;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	public void setUp() throws Exception {
		PrivateGroupManagerTestComponent component =
				DaggerPrivateGroupManagerTestComponent.builder().build();
		component.inject(this);
		injectEagerSingletons(component);

		assertTrue(testDir.mkdirs());
		File t0Dir = new File(testDir, AUTHOR1);
		t0 = DaggerPrivateGroupManagerTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(t0);
		File t1Dir = new File(testDir, AUTHOR2);
		t1 = DaggerPrivateGroupManagerTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(t1);

		identityManager0 = t0.getIdentityManager();
		identityManager1 = t1.getIdentityManager();
		contactManager0 = t0.getContactManager();
		contactManager1 = t1.getContactManager();
		groupManager0 = t0.getPrivateGroupManager();
		groupManager1 = t1.getPrivateGroupManager();
		sync0 = t0.getSyncSessionFactory();
		sync1 = t1.getSyncSessionFactory();

		// initialize waiters fresh for each test
		validationWaiter = new Waiter();
		deliveryWaiter = new Waiter();

		startLifecycles();
	}

	@Test
	public void testSendingMessage() throws Exception {
		defaultInit();

		// create and add test message
		long time = clock.currentTimeMillis();
		String body = "This is a test message!";
		MessageId previousMsgId =
				groupManager0.getPreviousMsgId(groupId0);
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, time, null, author0, body,
						previousMsgId);
		groupManager0.addLocalMessage(msg);
		assertEquals(msg.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// sync test message
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);

		// assert that message arrived as expected
		Collection<GroupMessageHeader> headers =
				groupManager1.getHeaders(groupId0);
		assertEquals(3, headers.size());
		GroupMessageHeader header = null;
		for (GroupMessageHeader h : headers) {
			if (!(h instanceof JoinMessageHeader)) {
				header = h;
			}
		}
		assertTrue(header != null);
		assertFalse(header.isRead());
		assertEquals(author0, header.getAuthor());
		assertEquals(time, header.getTimestamp());
		assertEquals(VERIFIED, header.getAuthorStatus());
		assertEquals(body, groupManager1.getMessageBody(header.getId()));
		GroupCount count = groupManager1.getGroupCount(groupId0);
		assertEquals(2, count.getUnreadCount());
		assertEquals(time, count.getLatestMsgTime());
		assertEquals(3, count.getMsgCount());
	}

	@Test
	public void testMessageWithWrongPreviousMsgId() throws Exception {
		defaultInit();

		// create and add test message with no previousMsgId
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(), null,
						author0, "test", null);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with random previousMsgId
		MessageId previousMsgId = new MessageId(TestUtils.getRandomId());
		msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(), null,
						author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with wrong previousMsgId
		previousMsgId = groupManager1.getPreviousMsgId(groupId0);
		msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(), null,
						author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with previousMsgId of newMemberMsg
		previousMsgId = newMemberMsg0.getMessage().getId();
		msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(), null,
						author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());
	}

	@Test
	public void testMessageWithWrongParentMsgId() throws Exception {
		defaultInit();

		// create and add test message with random parentMsgId
		MessageId parentMsgId = new MessageId(TestUtils.getRandomId());
		MessageId previousMsgId = groupManager0.getPreviousMsgId(groupId0);
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(),
						parentMsgId, author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with wrong parentMsgId
		parentMsgId = previousMsgId;
		msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(),
						parentMsgId, author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());
	}

	@Test
	public void testMessageWithWrongTimestamp() throws Exception {
		defaultInit();

		// create and add test message with wrong timestamp
		MessageId previousMsgId = groupManager0.getPreviousMsgId(groupId0);
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, 42, null, author0, "test",
						previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with good timestamp
		long time = clock.currentTimeMillis();
		msg = groupMessageFactory
				.createGroupMessage(groupId0, time, null, author0, "test",
						previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		assertEquals(3, groupManager1.getHeaders(groupId0).size());

		// create and add test message with same timestamp as previous message
		previousMsgId = msg.getMessage().getId();
		msg = groupMessageFactory
				.createGroupMessage(groupId0, time, previousMsgId, author0,
						"test2", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that message did not arrive
		assertEquals(3, groupManager1.getHeaders(groupId0).size());
	}

	@Test
	public void testWrongJoinMessages() throws Exception {
		addDefaultIdentities();
		addDefaultContacts();
		listenToEvents();

		// author0 joins privateGroup0 with later timestamp
		long joinTime = clock.currentTimeMillis();
		GroupMessage newMemberMsg = groupMessageFactory
				.createNewMemberMessage(groupId0, joinTime, author0, author0);
		GroupMessage joinMsg = groupMessageFactory
				.createJoinMessage(groupId0, joinTime + 1, author0,
						newMemberMsg.getMessage().getId());
		groupManager0.addPrivateGroup(privateGroup0, newMemberMsg, joinMsg);
		assertEquals(joinMsg.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// make group visible to 1
		Transaction txn0 = t0.getDatabaseComponent().startTransaction(false);
		t0.getDatabaseComponent()
				.setVisibleToContact(txn0, contactId1, privateGroup0.getId(),
						true);
		txn0.setComplete();
		t0.getDatabaseComponent().endTransaction(txn0);

		// author1 joins privateGroup0 and refers to wrong NEW_MEMBER message
		joinMsg = groupMessageFactory
				.createJoinMessage(groupId0, joinTime, author1,
						newMemberMsg.getMessage().getId());
		joinTime = clock.currentTimeMillis();
		newMemberMsg = groupMessageFactory
				.createNewMemberMessage(groupId0, joinTime, author0, author1);
		groupManager1.addPrivateGroup(privateGroup0, newMemberMsg, joinMsg);
		assertEquals(joinMsg.getMessage().getId(),
				groupManager1.getPreviousMsgId(groupId0));

		// make group visible to 0
		Transaction txn1 = t1.getDatabaseComponent().startTransaction(false);
		t1.getDatabaseComponent()
				.setVisibleToContact(txn1, contactId0, privateGroup0.getId(),
						true);
		txn1.setComplete();
		t1.getDatabaseComponent().endTransaction(txn1);

		// sync join messages
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		validationWaiter.await(TIMEOUT, 1);

		// assert that 0 never joined the group from 1's perspective
		assertEquals(1, groupManager1.getHeaders(groupId0).size());

		sync1To0();
		deliveryWaiter.await(TIMEOUT, 1);
		validationWaiter.await(TIMEOUT, 1);

		// assert that 1 never joined the group from 0's perspective
		assertEquals(1, groupManager0.getHeaders(groupId0).size());
	}

	@After
	public void tearDown() throws Exception {
		stopLifecycles();
		TestUtils.deleteTestDirectory(testDir);
	}

	private class Listener implements EventListener {
		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				if (!event.isLocal()) {
					if (event.getState() == DELIVERED) {
						LOG.info("Delivered new message");
						deliveryWaiter.resume();
					} else if (event.getState() == INVALID ||
							event.getState() == PENDING) {
						LOG.info("Validated new " + event.getState().name() +
								" message");
						validationWaiter.resume();
					}
				}
			}
		}
	}

	private void defaultInit() throws Exception {
		addDefaultIdentities();
		addDefaultContacts();
		listenToEvents();
		addGroup();
	}

	private void addDefaultIdentities() throws DbException {
		KeyPair keyPair0 = crypto.generateSignatureKeyPair();
		byte[] publicKey0 = keyPair0.getPublic().getEncoded();
		byte[] privateKey0 = keyPair0.getPrivate().getEncoded();
		author0 = authorFactory
				.createLocalAuthor(AUTHOR1, publicKey0, privateKey0);
		identityManager0.addLocalAuthor(author0);
		privateGroup0 =
				privateGroupFactory.createPrivateGroup("Testgroup", author0);
		groupId0 = privateGroup0.getId();

		KeyPair keyPair1 = crypto.generateSignatureKeyPair();
		byte[] publicKey1 = keyPair1.getPublic().getEncoded();
		byte[] privateKey1 = keyPair1.getPrivate().getEncoded();
		author1 = authorFactory
				.createLocalAuthor(AUTHOR2, publicKey1, privateKey1);
		identityManager1.addLocalAuthor(author1);
	}

	private void addDefaultContacts() throws DbException {
		// sharer adds invitee as contact
		contactId1 = contactManager0.addContact(author1,
				author0.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		// invitee adds sharer back
		contactId0 = contactManager1.addContact(author0,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
	}

	private void listenToEvents() {
		Listener listener0 = new Listener();
		t0.getEventBus().addListener(listener0);
		Listener listener1 = new Listener();
		t1.getEventBus().addListener(listener1);
	}

	private void addGroup() throws Exception {
		// author0 joins privateGroup0
		long joinTime = clock.currentTimeMillis();
		newMemberMsg0 = groupMessageFactory
				.createNewMemberMessage(privateGroup0.getId(), joinTime,
						author0, author0);
		GroupMessage joinMsg = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author0,
						newMemberMsg0.getMessage().getId());
		groupManager0.addPrivateGroup(privateGroup0, newMemberMsg0, joinMsg);
		assertEquals(joinMsg.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// make group visible to 1
		Transaction txn0 = t0.getDatabaseComponent().startTransaction(false);
		t0.getDatabaseComponent()
				.setVisibleToContact(txn0, contactId1, privateGroup0.getId(),
						true);
		txn0.setComplete();
		t0.getDatabaseComponent().endTransaction(txn0);

		// author1 joins privateGroup0
		joinTime = clock.currentTimeMillis();
		GroupMessage newMemberMsg1 = groupMessageFactory
				.createNewMemberMessage(privateGroup0.getId(), joinTime,
						author0, author1);
		joinMsg = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author1,
						newMemberMsg1.getMessage().getId());
		groupManager1.addPrivateGroup(privateGroup0, newMemberMsg1, joinMsg);
		assertEquals(joinMsg.getMessage().getId(),
				groupManager1.getPreviousMsgId(groupId0));

		// make group visible to 0
		Transaction txn1 = t1.getDatabaseComponent().startTransaction(false);
		t1.getDatabaseComponent()
				.setVisibleToContact(txn1, contactId0, privateGroup0.getId(),
						true);
		txn1.setComplete();
		t1.getDatabaseComponent().endTransaction(txn1);

		// sync join messages
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 2);
		sync1To0();
		deliveryWaiter.await(TIMEOUT, 2);
	}

	private void sync0To1() throws IOException, TimeoutException {
		deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
	}

	private void sync1To0() throws IOException, TimeoutException {
		deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
	}

	private void deliverMessage(SyncSessionFactory fromSync, ContactId fromId,
			SyncSessionFactory toSync, ContactId toId, String debug)
			throws IOException, TimeoutException {

		if (debug != null) LOG.info("TEST: Sending message from " + debug);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Create an outgoing sync session
		SyncSession sessionFrom =
				fromSync.createSimplexOutgoingSession(toId, MAX_LATENCY, out);
		// Write whatever needs to be written
		sessionFrom.run();
		out.close();

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Create an incoming sync session
		SyncSession sessionTo = toSync.createIncomingSession(fromId, in);
		// Read whatever needs to be read
		sessionTo.run();
		in.close();
	}

	private void startLifecycles() throws InterruptedException {
		// Start the lifecycle manager and wait for it to finish
		lifecycleManager0 = t0.getLifecycleManager();
		lifecycleManager1 = t1.getLifecycleManager();
		lifecycleManager0.startServices();
		lifecycleManager1.startServices();
		lifecycleManager0.waitForStartup();
		lifecycleManager1.waitForStartup();
	}

	private void stopLifecycles() throws InterruptedException {
		// Clean up
		lifecycleManager0.stopServices();
		lifecycleManager1.stopServices();
		lifecycleManager0.waitForShutdown();
		lifecycleManager1.waitForShutdown();
	}

	private void injectEagerSingletons(
			PrivateGroupManagerTestComponent component) {
		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new PrivateGroupModule.EagerSingletons());
		component.inject(new CryptoModule.EagerSingletons());
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new PropertiesModule.EagerSingletons());
	}

}
