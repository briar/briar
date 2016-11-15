package org.briarproject;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageStateChangedEvent;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.GroupMember;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.JoinMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.Group;
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
import static org.briarproject.TestUtils.getRandomBytes;
import static org.briarproject.api.identity.Author.Status.VERIFIED;
import static org.briarproject.api.privategroup.Visibility.INVISIBLE;
import static org.briarproject.api.privategroup.Visibility.REVEALED_BY_CONTACT;
import static org.briarproject.api.privategroup.Visibility.REVEALED_BY_US;
import static org.briarproject.api.privategroup.Visibility.VISIBLE;
import static org.briarproject.api.privategroup.invitation.GroupInvitationManager.CLIENT_ID;
import static org.briarproject.api.sync.Group.Visibility.SHARED;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.api.sync.ValidationManager.State.PENDING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrivateGroupManagerTest extends BriarIntegrationTest {

	private LifecycleManager lifecycleManager0, lifecycleManager1,
			lifecycleManager2;
	private SyncSessionFactory sync0, sync1, sync2;
	private PrivateGroupManager groupManager0, groupManager1, groupManager2;
	private ContactManager contactManager0, contactManager1, contactManager2;
	private ContactId contactId01, contactId02, contactId1, contactId2;
	private IdentityManager identityManager0, identityManager1,
			identityManager2;
	private LocalAuthor author0, author1, author2;
	private DatabaseComponent db0, db1, db2;
	private PrivateGroup privateGroup0;
	private GroupId groupId0;

	@Inject
	Clock clock;
	@Inject
	AuthorFactory authorFactory;
	@Inject
	ClientHelper clientHelper;
	@Inject
	CryptoComponent crypto;
	@Inject
	ContactGroupFactory contactGroupFactory;
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
	private final String AUTHOR0 = "Author 0";
	private final String AUTHOR1 = "Author 1";
	private final String AUTHOR2 = "Author 2";

	private static final Logger LOG =
			Logger.getLogger(PrivateGroupManagerTest.class.getName());

	private PrivateGroupManagerTestComponent t0, t1, t2;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	public void setUp() throws Exception {
		PrivateGroupManagerTestComponent component =
				DaggerPrivateGroupManagerTestComponent.builder().build();
		component.inject(this);

		assertTrue(testDir.mkdirs());
		File t0Dir = new File(testDir, AUTHOR0);
		t0 = DaggerPrivateGroupManagerTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(t0);
		File t1Dir = new File(testDir, AUTHOR1);
		t1 = DaggerPrivateGroupManagerTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(t1);
		File t2Dir = new File(testDir, AUTHOR2);
		t2 = DaggerPrivateGroupManagerTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t2Dir)).build();
		injectEagerSingletons(t2);

		identityManager0 = t0.getIdentityManager();
		identityManager1 = t1.getIdentityManager();
		identityManager2 = t2.getIdentityManager();
		contactManager0 = t0.getContactManager();
		contactManager1 = t1.getContactManager();
		contactManager2 = t2.getContactManager();
		db0 = t0.getDatabaseComponent();
		db1 = t1.getDatabaseComponent();
		db2 = t2.getDatabaseComponent();
		groupManager0 = t0.getPrivateGroupManager();
		groupManager1 = t1.getPrivateGroupManager();
		groupManager2 = t2.getPrivateGroupManager();
		sync0 = t0.getSyncSessionFactory();
		sync1 = t1.getSyncSessionFactory();
		sync2 = t2.getSyncSessionFactory();

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
		@SuppressWarnings("ConstantConditions")
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
	public void testWrongJoinMessages1() throws Exception {
		addDefaultIdentities();
		addDefaultContacts();
		listenToEvents();

		// author0 joins privateGroup0 with wrong join message
		long joinTime = clock.currentTimeMillis();
		GroupMessage joinMsg0 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author0,
						joinTime, getRandomBytes(12));
		groupManager0.addPrivateGroup(privateGroup0, joinMsg0, true);
		assertEquals(joinMsg0.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// share the group with 1
		Transaction txn0 = db0.startTransaction(false);
		db0.setGroupVisibility(txn0, contactId1, privateGroup0.getId(), SHARED);
		db0.commitTransaction(txn0);
		db0.endTransaction(txn0);

		// author1 joins privateGroup0 with wrong timestamp
		joinTime = clock.currentTimeMillis();
		long inviteTime = joinTime;
		Group invitationGroup = contactGroupFactory
				.createContactGroup(CLIENT_ID, author0.getId(),
						author1.getId());
		BdfList toSign = BdfList.of(0, inviteTime, invitationGroup.getId(),
				privateGroup0.getId());
		byte[] creatorSignature =
				clientHelper.sign(toSign, author0.getPrivateKey());
		GroupMessage joinMsg1 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author1,
						inviteTime, creatorSignature);
		groupManager1.addPrivateGroup(privateGroup0, joinMsg1, false);
		assertEquals(joinMsg1.getMessage().getId(),
				groupManager1.getPreviousMsgId(groupId0));

		// share the group with 0
		Transaction txn1 = db1.startTransaction(false);
		db1.setGroupVisibility(txn1, contactId01, privateGroup0.getId(),
				SHARED);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);

		// sync join messages
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that 0 never joined the group from 1's perspective
		assertEquals(1, groupManager1.getHeaders(groupId0).size());

		sync1To0();
		validationWaiter.await(TIMEOUT, 1);

		// assert that 1 never joined the group from 0's perspective
		assertEquals(1, groupManager0.getHeaders(groupId0).size());
	}

	@Test
	public void testWrongJoinMessages2() throws Exception {
		addDefaultIdentities();
		addDefaultContacts();
		listenToEvents();

		// author0 joins privateGroup0 with wrong member's join message
		long joinTime = clock.currentTimeMillis();
		long inviteTime = joinTime - 1;
		Group invitationGroup = contactGroupFactory
				.createContactGroup(CLIENT_ID, author0.getId(),
						author0.getId());
		BdfList toSign = BdfList.of(0, inviteTime, invitationGroup.getId(),
				privateGroup0.getId());
		byte[] creatorSignature =
				clientHelper.sign(toSign, author0.getPrivateKey());
		// join message should not include invite time and creator's signature
		GroupMessage joinMsg0 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author0,
						inviteTime, creatorSignature);
		groupManager0.addPrivateGroup(privateGroup0, joinMsg0, true);
		assertEquals(joinMsg0.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// share the group with 1
		Transaction txn0 = db0.startTransaction(false);
		db0.setGroupVisibility(txn0, contactId1, privateGroup0.getId(), SHARED);
		db0.commitTransaction(txn0);
		db0.endTransaction(txn0);

		// author1 joins privateGroup0 with wrong signature in join message
		joinTime = clock.currentTimeMillis();
		inviteTime = joinTime - 1;
		invitationGroup = contactGroupFactory
				.createContactGroup(CLIENT_ID, author0.getId(),
						author1.getId());
		toSign = BdfList.of(0, inviteTime, invitationGroup.getId(),
				privateGroup0.getId());
		// signature uses joiner's key, not creator's key
		creatorSignature = clientHelper.sign(toSign, author1.getPrivateKey());
		GroupMessage joinMsg1 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author1,
						inviteTime, creatorSignature);
		groupManager1.addPrivateGroup(privateGroup0, joinMsg1, false);
		assertEquals(joinMsg1.getMessage().getId(),
				groupManager1.getPreviousMsgId(groupId0));

		// share the group with 0
		Transaction txn1 = db1.startTransaction(false);
		db1.setGroupVisibility(txn1, contactId01, privateGroup0.getId(), SHARED);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);

		// sync join messages
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);

		// assert that 0 never joined the group from 1's perspective
		assertEquals(1, groupManager1.getHeaders(groupId0).size());

		sync1To0();
		validationWaiter.await(TIMEOUT, 1);

		// assert that 1 never joined the group from 0's perspective
		assertEquals(1, groupManager0.getHeaders(groupId0).size());
	}

	@Test
	public void testGetMembers() throws Exception {
		defaultInit();

		Collection<GroupMember> members0 = groupManager0.getMembers(groupId0);
		assertEquals(2, members0.size());
		for (GroupMember m : members0) {
			if (m.getAuthor().equals(author0)) {
				assertEquals(VISIBLE, m.getVisibility());
			} else {
				assertEquals(author1, m.getAuthor());
				assertEquals(VISIBLE, m.getVisibility());
			}
		}

		Collection<GroupMember> members1 = groupManager1.getMembers(groupId0);
		assertEquals(2, members1.size());
		for (GroupMember m : members1) {
			if (m.getAuthor().equals(author1)) {
				assertEquals(VISIBLE, m.getVisibility());
			} else {
				assertEquals(author0, m.getAuthor());
				assertEquals(VISIBLE, m.getVisibility());
			}
		}
	}

	@Test
	public void testJoinMessages() throws Exception {
		defaultInit();

		Collection<GroupMessageHeader> headers0 =
				groupManager0.getHeaders(groupId0);
		for (GroupMessageHeader h : headers0) {
			if (h instanceof JoinMessageHeader) {
				JoinMessageHeader j = (JoinMessageHeader) h;
				// all relationships of the creator are visible
				assertEquals(VISIBLE, j.getVisibility());
			}
		}

		Collection<GroupMessageHeader> headers1 =
				groupManager1.getHeaders(groupId0);
		for (GroupMessageHeader h : headers1) {
			if (h instanceof JoinMessageHeader) {
				JoinMessageHeader j = (JoinMessageHeader) h;
				if (h.getAuthor().equals(author1))
					// we are visible to ourselves
					assertEquals(VISIBLE, j.getVisibility());
				else
					// our relationship to the creator is visible
					assertEquals(VISIBLE, j.getVisibility());
			}
		}
	}

	@Test
	public void testRevealingRelationships() throws Exception {
		defaultInit();

		// share the group with 2
		Transaction txn0 = db0.startTransaction(false);
		db0.setGroupVisibility(txn0, contactId2, privateGroup0.getId(), SHARED);
		db0.commitTransaction(txn0);
		db0.endTransaction(txn0);

		// author2 joins privateGroup0
		long joinTime = clock.currentTimeMillis();
		long inviteTime = joinTime - 1;
		Group invitationGroup = contactGroupFactory
				.createContactGroup(CLIENT_ID, author0.getId(),
						author2.getId());
		BdfList toSign = BdfList.of(0, inviteTime, invitationGroup.getId(),
				privateGroup0.getId());
		byte[] creatorSignature =
				clientHelper.sign(toSign, author0.getPrivateKey());
		GroupMessage joinMsg2 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author2,
						inviteTime, creatorSignature);
		Transaction txn2 = db2.startTransaction(false);
		groupManager2.addPrivateGroup(txn2, privateGroup0, joinMsg2, false);

		// share the group with 0
		db2.setGroupVisibility(txn2, contactId01, privateGroup0.getId(),
				SHARED);
		db2.commitTransaction(txn2);
		db2.endTransaction(txn2);

		// sync join messages
		deliverMessage(sync2, contactId2, sync0, contactId02, "2 to 0");
		deliveryWaiter.await(TIMEOUT, 1);
		deliverMessage(sync0, contactId02, sync2, contactId2, "0 to 2");
		deliveryWaiter.await(TIMEOUT, 2);
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);

		// check that everybody sees everybody else as joined
		Collection<GroupMember> members0 = groupManager0.getMembers(groupId0);
		assertEquals(3, members0.size());
		Collection<GroupMember> members1 = groupManager1.getMembers(groupId0);
		assertEquals(3, members1.size());
		Collection<GroupMember> members2 = groupManager2.getMembers(groupId0);
		assertEquals(3, members2.size());

		// assert that contact relationship is not revealed initially
		for (GroupMember m : members1) {
			if (m.getAuthor().equals(author2)) {
				assertEquals(INVISIBLE, m.getVisibility());
			}
		}
		for (GroupMember m : members2) {
			if (m.getAuthor().equals(author1)) {
				assertEquals(INVISIBLE, m.getVisibility());
			}
		}

		// reveal contact relationship
		Transaction txn1 = db1.startTransaction(false);
		groupManager1
				.relationshipRevealed(txn1, groupId0, author2.getId(), false);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);
		txn2 = db2.startTransaction(false);
		groupManager2
				.relationshipRevealed(txn2, groupId0, author1.getId(), true);
		db2.commitTransaction(txn2);
		db2.endTransaction(txn2);

		// assert that contact relationship is now revealed properly
		members1 = groupManager1.getMembers(groupId0);
		for (GroupMember m : members1) {
			if (m.getAuthor().equals(author2)) {
				assertEquals(REVEALED_BY_US, m.getVisibility());
			}
		}
		members2 = groupManager2.getMembers(groupId0);
		for (GroupMember m : members2) {
			if (m.getAuthor().equals(author1)) {
				assertEquals(REVEALED_BY_CONTACT, m.getVisibility());
			}
		}

		// assert that join messages reflect revealed relationship
		Collection<GroupMessageHeader> headers1 =
				groupManager1.getHeaders(groupId0);
		for (GroupMessageHeader h : headers1) {
			if (h instanceof JoinMessageHeader) {
				JoinMessageHeader j = (JoinMessageHeader) h;
				if (h.getAuthor().equals(author2))
					// 1 revealed the relationship to 2
					assertEquals(REVEALED_BY_US, j.getVisibility());
				else
					// 1's other relationship (to 1 and creator) are visible
					assertEquals(VISIBLE, j.getVisibility());
			}
		}
		Collection<GroupMessageHeader> headers2 =
				groupManager2.getHeaders(groupId0);
		for (GroupMessageHeader h : headers2) {
			if (h instanceof JoinMessageHeader) {
				JoinMessageHeader j = (JoinMessageHeader) h;
				if (h.getAuthor().equals(author1))
					// 2's relationship was revealed by 1
					assertEquals(REVEALED_BY_CONTACT, j.getVisibility());
				else
					// 2's other relationship (to 2 and creator) are visible
					assertEquals(VISIBLE, j.getVisibility());
			}
		}
	}

	@Test
	public void testDissolveGroup() throws Exception {
		defaultInit();

		// group is not dissolved initially
		assertFalse(groupManager1.isDissolved(groupId0));

		// creator dissolves group
		Transaction txn1 = db1.startTransaction(false);
		groupManager1.markGroupDissolved(txn1, groupId0);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);

		// group is dissolved now
		assertTrue(groupManager1.isDissolved(groupId0));
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
				.createLocalAuthor(AUTHOR0, publicKey0, privateKey0);
		identityManager0.registerLocalAuthor(author0);
		privateGroup0 =
				privateGroupFactory.createPrivateGroup("Testgroup", author0);
		groupId0 = privateGroup0.getId();

		KeyPair keyPair1 = crypto.generateSignatureKeyPair();
		byte[] publicKey1 = keyPair1.getPublic().getEncoded();
		byte[] privateKey1 = keyPair1.getPrivate().getEncoded();
		author1 = authorFactory
				.createLocalAuthor(AUTHOR1, publicKey1, privateKey1);
		identityManager1.registerLocalAuthor(author1);

		KeyPair keyPair2 = crypto.generateSignatureKeyPair();
		byte[] publicKey2 = keyPair2.getPublic().getEncoded();
		byte[] privateKey2 = keyPair2.getPrivate().getEncoded();
		author2 = authorFactory
				.createLocalAuthor(AUTHOR2, publicKey2, privateKey2);
		identityManager2.registerLocalAuthor(author2);
	}

	private void addDefaultContacts() throws DbException {
		// creator adds invitee as contact
		contactId1 = contactManager0
				.addContact(author1, author0.getId(), master,
						clock.currentTimeMillis(), true, true, true);
		// invitee adds creator back
		contactId01 = contactManager1
				.addContact(author0, author1.getId(), master,
						clock.currentTimeMillis(), true, true, true);
		// creator adds invitee as contact
		contactId2 = contactManager0
				.addContact(author2, author0.getId(), master,
						clock.currentTimeMillis(), true, true, true);
		// invitee adds creator back
		contactId02 = contactManager2
				.addContact(author0, author2.getId(), master,
						clock.currentTimeMillis(), true, true, true);
	}

	private void listenToEvents() {
		Listener listener0 = new Listener();
		t0.getEventBus().addListener(listener0);
		Listener listener1 = new Listener();
		t1.getEventBus().addListener(listener1);
		Listener listener2 = new Listener();
		t2.getEventBus().addListener(listener2);
	}

	private void addGroup() throws Exception {
		// author0 joins privateGroup0
		long joinTime = clock.currentTimeMillis();
		GroupMessage joinMsg0 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author0);
		groupManager0.addPrivateGroup(privateGroup0, joinMsg0, true);
		assertEquals(joinMsg0.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// share the group with 1
		Transaction txn0 = db0.startTransaction(false);
		db0.setGroupVisibility(txn0, contactId1, privateGroup0.getId(), SHARED);
		db0.commitTransaction(txn0);
		db0.endTransaction(txn0);

		// author1 joins privateGroup0
		joinTime = clock.currentTimeMillis();
		long inviteTime = joinTime - 1;
		Group invitationGroup = contactGroupFactory
				.createContactGroup(CLIENT_ID, author0.getId(),
						author1.getId());
		BdfList toSign = BdfList.of(0, inviteTime, invitationGroup.getId(),
				privateGroup0.getId());
		byte[] creatorSignature =
				clientHelper.sign(toSign, author0.getPrivateKey());
		GroupMessage joinMsg1 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author1,
						inviteTime, creatorSignature);
		groupManager1.addPrivateGroup(privateGroup0, joinMsg1, false);

		// share the group with 0
		Transaction txn1 = db1.startTransaction(false);
		db1.setGroupVisibility(txn1, contactId01, privateGroup0.getId(),
				SHARED);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);
		assertEquals(joinMsg1.getMessage().getId(),
				groupManager1.getPreviousMsgId(groupId0));

		// sync join messages
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		sync1To0();
		deliveryWaiter.await(TIMEOUT, 1);
	}

	private void sync0To1() throws IOException, TimeoutException {
		deliverMessage(sync0, contactId01, sync1, contactId1, "0 to 1");
	}

	private void sync1To0() throws IOException, TimeoutException {
		deliverMessage(sync1, contactId1, sync0, contactId01, "1 to 0");
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
		lifecycleManager2 = t2.getLifecycleManager();
		lifecycleManager0.startServices(AUTHOR0);
		lifecycleManager1.startServices(AUTHOR1);
		lifecycleManager2.startServices(AUTHOR2);
		lifecycleManager0.waitForStartup();
		lifecycleManager1.waitForStartup();
		lifecycleManager2.waitForStartup();
	}

	private void stopLifecycles() throws InterruptedException {
		// Clean up
		lifecycleManager0.stopServices();
		lifecycleManager1.stopServices();
		lifecycleManager2.stopServices();
		lifecycleManager0.waitForShutdown();
		lifecycleManager1.waitForShutdown();
		lifecycleManager2.waitForShutdown();
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
