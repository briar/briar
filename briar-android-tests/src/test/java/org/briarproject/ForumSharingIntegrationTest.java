package org.briarproject;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.api.Bytes;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.api.event.MessageStateChangedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.forum.ForumInvitationResponse;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sharing.InvitationItem;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.sync.ValidationManager.State;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sharing.SharingModule;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.TestPluginsModule.MAX_LATENCY;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForumSharingIntegrationTest extends BriarTestCase {

	private LifecycleManager lifecycleManager0, lifecycleManager1, lifecycleManager2;
	private SyncSessionFactory sync0, sync1, sync2;
	private ForumManager forumManager0, forumManager1;
	private ContactManager contactManager0, contactManager1, contactManager2;
	private ContactId contactId0, contactId2, contactId1, contactId21;
	private IdentityManager identityManager0, identityManager1, identityManager2;
	private LocalAuthor author0, author1, author2;
	private Forum forum0;
	private SharerListener listener0, listener2;
	private InviteeListener listener1;

	@Inject
	Clock clock;
	@Inject
	AuthorFactory authorFactory;
	@Inject
	ForumPostFactory forumPostFactory;
	@Inject
	CryptoComponent cryptoComponent;

	// objects accessed from background threads need to be volatile
	private volatile ForumSharingManager forumSharingManager0;
	private volatile ForumSharingManager forumSharingManager1;
	private volatile ForumSharingManager forumSharingManager2;
	private volatile Waiter eventWaiter;
	private volatile Waiter msgWaiter;

	private final File testDir = TestUtils.getTestDirectory();
	private final SecretKey master = TestUtils.getSecretKey();
	private final int TIMEOUT = 15000;
	private final String SHARER = "Sharer";
	private final String INVITEE = "Invitee";
	private final String SHARER2 = "Sharer2";
	private boolean respond = true;

	private static final Logger LOG =
			Logger.getLogger(ForumSharingIntegrationTest.class.getName());

	private ForumSharingIntegrationTestComponent t0, t1, t2;

	@Rule
	public ExpectedException thrown=ExpectedException.none();

	@Before
	public void setUp() {
		ForumSharingIntegrationTestComponent component =
				DaggerForumSharingIntegrationTestComponent.builder().build();
		component.inject(this);
		injectEagerSingletons(component);

		assertTrue(testDir.mkdirs());
		File t0Dir = new File(testDir, SHARER);
		t0 = DaggerForumSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(t0);
		File t1Dir = new File(testDir, INVITEE);
		t1 = DaggerForumSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(t1);
		File t2Dir = new File(testDir, SHARER2);
		t2 = DaggerForumSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t2Dir)).build();
		injectEagerSingletons(t2);

		identityManager0 = t0.getIdentityManager();
		identityManager1 = t1.getIdentityManager();
		identityManager2 = t2.getIdentityManager();
		contactManager0 = t0.getContactManager();
		contactManager1 = t1.getContactManager();
		contactManager2 = t2.getContactManager();
		forumManager0 = t0.getForumManager();
		forumManager1 = t1.getForumManager();
		forumSharingManager0 = t0.getForumSharingManager();
		forumSharingManager1 = t1.getForumSharingManager();
		forumSharingManager2 = t2.getForumSharingManager();
		sync0 = t0.getSyncSessionFactory();
		sync1 = t1.getSyncSessionFactory();
		sync2 = t2.getSyncSessionFactory();

		// initialize waiters fresh for each test
		eventWaiter = new Waiter();
		msgWaiter = new Waiter();
	}

	@Test
	public void testSuccessfulSharing() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(0, forumSharingManager0.getInvitations().size());
			assertEquals(1, forumManager1.getForums().size());

			// invitee has one invitation message from sharer
			List<InvitationMessage> list =
					new ArrayList<>(forumSharingManager1
							.getInvitationMessages(contactId0));
			assertEquals(2, list.size());
			// check other things are alright with the forum message
			for (InvitationMessage m : list) {
				if (m instanceof ForumInvitationRequest) {
					ForumInvitationRequest invitation =
							(ForumInvitationRequest) m;
					assertFalse(invitation.isAvailable());
					assertEquals(forum0.getName(), invitation.getForumName());
					assertEquals(contactId1, invitation.getContactId());
					assertEquals("Hi!", invitation.getMessage());
				} else {
					ForumInvitationResponse response =
							(ForumInvitationResponse) m;
					assertEquals(contactId0, response.getContactId());
					assertTrue(response.wasAccepted());
					assertTrue(response.isLocal());
				}
			}
			// sharer has own invitation message and response
			assertEquals(2,
					forumSharingManager0.getInvitationMessages(contactId1)
							.size());
			// forum can not be shared again
			Contact c1 = contactManager0.getContact(contactId1);
			assertFalse(forumSharingManager0.canBeShared(forum0.getId(), c1));
			Contact c0 = contactManager1.getContact(contactId0);
			assertFalse(forumSharingManager1.canBeShared(forum0.getId(), c0));
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testDeclinedSharing() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(false);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, null);

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was not added
			assertEquals(0, forumSharingManager0.getInvitations().size());
			assertEquals(0, forumManager1.getForums().size());
			// forum is no longer available to invitee who declined
			assertEquals(0, forumSharingManager1.getInvitations().size());

			// invitee has one invitation message from sharer and one response
			List<InvitationMessage> list =
					new ArrayList<>(forumSharingManager1
							.getInvitationMessages(contactId0));
			assertEquals(2, list.size());
			// check things are alright with the forum message
			for (InvitationMessage m : list) {
				if (m instanceof ForumInvitationRequest) {
					ForumInvitationRequest invitation =
							(ForumInvitationRequest) m;
					assertFalse(invitation.isAvailable());
					assertEquals(forum0.getName(), invitation.getForumName());
					assertEquals(contactId1, invitation.getContactId());
					assertEquals(null, invitation.getMessage());
				} else {
					ForumInvitationResponse response =
							(ForumInvitationResponse) m;
					assertEquals(contactId0, response.getContactId());
					assertFalse(response.wasAccepted());
					assertTrue(response.isLocal());
				}
			}
			// sharer has own invitation message and response
			assertEquals(2,
					forumSharingManager0.getInvitationMessages(contactId1)
							.size());
			// forum can be shared again
			Contact c1 = contactManager0.getContact(contactId1);
			assertTrue(forumSharingManager0.canBeShared(forum0.getId(), c1));
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testInviteeLeavesAfterFinished() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(0, forumSharingManager0.getInvitations().size());
			assertEquals(1, forumManager1.getForums().size());
			assertTrue(forumManager1.getForums().contains(forum0));

			// sharer shares forum with invitee
			Contact c1 = contactManager0.getContact(contactId1);
			assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
					.contains(c1));
			// invitee gets forum shared by sharer
			Contact contact0 = contactManager1.getContact(contactId1);
			assertTrue(forumSharingManager1.getSharedBy(forum0.getId())
					.contains(contact0));

			// invitee un-subscribes from forum
			forumManager1.removeForum(forum0);

			// send leave message to sharer
			syncToSharer();

			// forum is gone
			assertEquals(0, forumSharingManager0.getInvitations().size());
			assertEquals(0, forumManager1.getForums().size());

			// sharer no longer shares forum with invitee
			assertFalse(forumSharingManager0.getSharedWith(forum0.getId())
					.contains(c1));
			// invitee no longer gets forum shared by sharer
			assertFalse(forumSharingManager1.getSharedBy(forum0.getId())
					.contains(contact0));
			// forum can be shared again
			assertTrue(forumSharingManager0.canBeShared(forum0.getId(), c1));
			Contact c0 = contactManager1.getContact(contactId0);
			assertTrue(forumSharingManager1.canBeShared(forum0.getId(), c0));
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSharerLeavesAfterFinished() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, null);

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(0, forumSharingManager0.getInvitations().size());
			assertEquals(1, forumManager1.getForums().size());
			assertTrue(forumManager1.getForums().contains(forum0));

			// sharer shares forum with invitee
			Contact c1 = contactManager0.getContact(contactId1);
			assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
					.contains(c1));
			// invitee gets forum shared by sharer
			Contact contact0 = contactManager1.getContact(contactId1);
			assertTrue(forumSharingManager1.getSharedBy(forum0.getId())
					.contains(contact0));

			// sharer un-subscribes from forum
			forumManager0.removeForum(forum0);

			// send leave message to invitee
			syncToInvitee();

			// forum is gone for sharer, but not invitee
			assertEquals(0, forumManager0.getForums().size());
			assertEquals(1, forumManager1.getForums().size());

			// invitee no longer shares forum with sharer
			Contact c0 = contactManager1.getContact(contactId0);
			assertFalse(forumSharingManager1.getSharedWith(forum0.getId())
					.contains(c0));
			// sharer no longer gets forum shared by invitee
			assertFalse(forumSharingManager1.getSharedBy(forum0.getId())
					.contains(contact0));
			// forum can be shared again
			assertTrue(forumSharingManager1.canBeShared(forum0.getId(), c0));
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSharerLeavesBeforeResponse() throws Exception {
		startLifecycles();
		try {
			// initialize except event listeners
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, null);

			// sharer un-subscribes from forum
			forumManager0.removeForum(forum0);

			// prevent invitee response before syncing messages
			respond = false;

			// sync first request message and leave message
			deliverMessage(sync0, contactId0, sync1, contactId1, 2,
					"Sharer to Invitee");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// ensure that invitee has no forum invitations available
			assertEquals(0, forumSharingManager1.getInvitations().size());
			assertEquals(0, forumManager1.getForums().size());

			// Try again, this time allow the response
			addForumForSharer();
			respond = true;

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, null);

			// sharer un-subscribes from forum
			forumManager0.removeForum(forum0);

			// sync first request message and leave message
			deliverMessage(sync0, contactId0, sync1, contactId1, 2,
					"Sharer to Invitee");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// ensure that invitee has no forum invitations available
			assertEquals(0, forumSharingManager1.getInvitations().size());
			assertEquals(1, forumManager1.getForums().size());
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSessionIdReuse() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(1, forumManager1.getForums().size());

			// reset event received state
			listener1.requestReceived = false;

			// get SessionId from invitation
			List<InvitationMessage> list = new ArrayList<>(
					forumSharingManager1
							.getInvitationMessages(contactId0));
			assertEquals(2, list.size());
			InvitationMessage msg = list.get(0);
			SessionId sessionId = msg.getSessionId();
			assertEquals(sessionId, list.get(1).getSessionId());

			// get all sorts of stuff needed to send a message
			DatabaseComponent db = t0.getDatabaseComponent();
			MessageQueueManager queue = t0.getMessageQueueManager();
			Contact c1 = contactManager0.getContact(contactId1);
			PrivateGroupFactory groupFactory = t0.getPrivateGroupFactory();
			Group group = groupFactory
					.createPrivateGroup(forumSharingManager0.getClientId(), c1);
			long time = clock.currentTimeMillis();
			BdfList bodyList = BdfList.of(SHARE_MSG_TYPE_INVITATION,
					sessionId.getBytes(),
					TestUtils.getRandomString(42),
					TestUtils.getRandomBytes(FORUM_SALT_LENGTH)
			);
			byte[] body = t0.getClientHelper().toByteArray(bodyList);

			// add the message to the queue
			Transaction txn = db.startTransaction(false);
			try {
				queue.sendMessage(txn, group, time, body, new Metadata());
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}

			// actually send the message
			syncToInvitee();
			// make sure there was no new request received
			assertFalse(listener1.requestReceived);
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSharingSameForumWithEachOther() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(1, forumManager1.getForums().size());
			assertEquals(2,
					forumSharingManager0.getInvitationMessages(contactId1)
							.size());

			// invitee now shares same forum back
			forumSharingManager1.sendInvitation(forum0.getId(),
					contactId0,
					"I am re-sharing this forum with you.");

			// sync re-share invitation
			syncToSharer();

			// make sure that no new request was received
			assertFalse(listener0.requestReceived);
			assertEquals(2,
					forumSharingManager0.getInvitationMessages(contactId1)
							.size());
			assertEquals(0, forumSharingManager0.getInvitations().size());
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSharingSameForumWithEachOtherAtSameTime() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// invitee adds the same forum
			DatabaseComponent db1 = t1.getDatabaseComponent();
			Transaction txn = db1.startTransaction(false);
			db1.addGroup(txn, forum0.getGroup());
			txn.setComplete();
			db1.endTransaction(txn);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");

			// invitee now shares same forum back
			forumSharingManager1.sendInvitation(forum0.getId(),
					contactId0, "I am re-sharing this forum with you.");

			// find out who should be Alice, because of random keys
			Bytes key0 = new Bytes(author0.getPublicKey());
			Bytes key1 = new Bytes(author1.getPublicKey());

			// only now sync first request message
			boolean alice = key1.compareTo(key0) < 0;
			syncToInvitee();
			if (alice) {
				assertFalse(listener1.requestReceived);
			} else {
				eventWaiter.await(TIMEOUT, 1);
				assertTrue(listener1.requestReceived);
			}

			// sync second invitation
			alice = key0.compareTo(key1) < 0;
			syncToSharer();
			if (alice) {
				assertFalse(listener0.requestReceived);

				// sharer did not receive request, but response to own request
				eventWaiter.await(TIMEOUT, 1);
				assertTrue(listener0.responseReceived);

				assertEquals(2, forumSharingManager0
						.getInvitationMessages(contactId1).size());
				assertEquals(3, forumSharingManager1
						.getInvitationMessages(contactId0).size());
			} else {
				eventWaiter.await(TIMEOUT, 1);
				assertTrue(listener0.requestReceived);

				// send response from sharer to invitee and make sure it arrived
				syncToInvitee();
				eventWaiter.await(TIMEOUT, 1);
				assertTrue(listener1.responseReceived);

				assertEquals(3, forumSharingManager0
						.getInvitationMessages(contactId1).size());
				assertEquals(2, forumSharingManager1
						.getInvitationMessages(contactId0).size());
			}
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testContactRemoved() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(1, forumManager1.getForums().size());
			assertEquals(1,
					forumSharingManager0.getSharedWith(forum0.getId()).size());

			// remember SessionId from invitation
			List<InvitationMessage> list = new ArrayList<>(
					forumSharingManager1
							.getInvitationMessages(contactId0));
			assertEquals(2, list.size());
			InvitationMessage msg = list.get(0);
			SessionId sessionId = msg.getSessionId();
			assertEquals(sessionId, list.get(1).getSessionId());

			// contacts now remove each other
			contactManager0.removeContact(contactId1);
			contactManager2.removeContact(contactId21);
			contactManager1.removeContact(contactId0);
			contactManager1.removeContact(contactId2);

			// make sure sharer does share the forum with nobody now
			assertEquals(0,
					forumSharingManager0.getSharedWith(forum0.getId()).size());

			// contacts add each other again
			addDefaultContacts();

			// get all sorts of stuff needed to send a message
			DatabaseComponent db = t0.getDatabaseComponent();
			MessageQueueManager queue = t0.getMessageQueueManager();
			Contact c1 = contactManager0.getContact(contactId1);
			PrivateGroupFactory groupFactory = t0.getPrivateGroupFactory();
			Group group = groupFactory
					.createPrivateGroup(forumSharingManager0.getClientId(), c1);
			long time = clock.currentTimeMillis();

			// construct a new message re-using the old SessionId
			BdfList bodyList = BdfList.of(SHARE_MSG_TYPE_INVITATION,
					sessionId.getBytes(),
					TestUtils.getRandomString(42),
					TestUtils.getRandomBytes(FORUM_SALT_LENGTH)
			);
			byte[] body = t0.getClientHelper().toByteArray(bodyList);

			// add the message to the queue
			Transaction txn = db.startTransaction(false);
			try {
				queue.sendMessage(txn, group, time, body, new Metadata());
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}

			// actually send the message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			// make sure the new request was received with the same sessionId
			// as proof that the state got deleted along with contacts
			assertTrue(listener1.requestReceived);
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testTwoContactsShareSameForum() throws Exception {
		startLifecycles();
		try {
			// initialize
			addDefaultIdentities();
			addDefaultContacts();
			addForumForSharer();

			// second sharer adds the same forum
			DatabaseComponent db2 = t2.getDatabaseComponent();
			Transaction txn = db2.startTransaction(false);
			db2.addGroup(txn, forum0.getGroup());
			txn.setComplete();
			db2.endTransaction(txn);

			// add listeners
			listener0 = new SharerListener();
			t0.getEventBus().addListener(listener0);
			listener1 = new InviteeListener(true, false);
			t1.getEventBus().addListener(listener1);
			listener2 = new SharerListener();
			t2.getEventBus().addListener(listener2);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");
			// sync first request message
			syncToInvitee();

			// second sharer sends invitation for same forum
			forumSharingManager2
					.sendInvitation(forum0.getId(), contactId1, null);
			// sync second request message
			deliverMessage(sync2, contactId2, sync1, contactId1, 1,
					"Sharer2 to Invitee");

			// make sure we now have two invitations to the same forum available
			Collection<InvitationItem> forums =
					forumSharingManager1.getInvitations();
			assertEquals(1, forums.size());
			assertEquals(2, forums.iterator().next().getNewSharers().size());
			assertEquals(forum0, forums.iterator().next().getShareable());
			assertEquals(2,
					forumSharingManager1.getSharedBy(forum0.getId()).size());

			// make sure both sharers actually share the forum
			Collection<Contact> contacts =
					forumSharingManager1.getSharedBy(forum0.getId());
			assertEquals(2, contacts.size());

			// answer second request
			Contact c2 = contactManager1.getContact(contactId2);
			forumSharingManager1.respondToInvitation(forum0, c2, true);
			// sync response
			deliverMessage(sync1, contactId21, sync2, contactId2, 1,
					"Invitee to Sharer2");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener2.responseReceived);

			// answer first request
			Contact c0 =
					contactManager1.getContact(contactId0);
			forumSharingManager1.respondToInvitation(forum0, c0, true);
			// sync response
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSyncAfterReSharing() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);

			// sharer posts into the forum
			long time = clock.currentTimeMillis();
			byte[] body = TestUtils.getRandomBytes(42);
			KeyParser keyParser = cryptoComponent.getSignatureKeyParser();
			PrivateKey key = keyParser.parsePrivateKey(author0.getPrivateKey());
			ForumPost p = forumPostFactory
					.createPseudonymousPost(forum0.getId(), time, null, author0,
							"text/plain", body, key);
			forumManager0.addLocalPost(p);

			// sync forum post
			syncToInvitee();

			// make sure forum post arrived
			Collection<ForumPostHeader> headers =
					forumManager1.getPostHeaders(forum0.getId());
			assertEquals(1, headers.size());
			ForumPostHeader header = headers.iterator().next();
			assertEquals(p.getMessage().getId(), header.getId());
			assertEquals(author0, header.getAuthor());

			// now invitee creates a post
			time = clock.currentTimeMillis();
			body = TestUtils.getRandomBytes(42);
			key = keyParser.parsePrivateKey(author1.getPrivateKey());
			p = forumPostFactory
					.createPseudonymousPost(forum0.getId(), time, null, author1,
							"text/plain", body, key);
			forumManager1.addLocalPost(p);

			// sync forum post
			syncToSharer();

			// make sure forum post arrived
			headers = forumManager1.getPostHeaders(forum0.getId());
			assertEquals(2, headers.size());
			boolean found = false;
			for (ForumPostHeader h : headers) {
				if (p.getMessage().getId().equals(h.getId())) {
					found = true;
					assertEquals(author1, h.getAuthor());
				}
			}
			assertTrue(found);

			// contacts remove each other
			contactManager0.removeContact(contactId1);
			contactManager1.removeContact(contactId0);
			contactManager1.removeContact(contactId2);
			contactManager2.removeContact(contactId21);

			// contacts add each other back
			addDefaultContacts();

			// send invitation again
			forumSharingManager0
					.sendInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);

			// now invitee creates a post
			time = clock.currentTimeMillis();
			body = TestUtils.getRandomBytes(42);
			key = keyParser.parsePrivateKey(author1.getPrivateKey());
			p = forumPostFactory
					.createPseudonymousPost(forum0.getId(), time, null, author1,
							"text/plain", body, key);
			forumManager1.addLocalPost(p);

			// sync forum post
			syncToSharer();

			// make sure forum post arrived
			headers = forumManager1.getPostHeaders(forum0.getId());
			assertEquals(3, headers.size());
			found = false;
			for (ForumPostHeader h : headers) {
				if (p.getMessage().getId().equals(h.getId())) {
					found = true;
					assertEquals(author1, h.getAuthor());
				}
			}
			assertTrue(found);
		} finally {
			stopLifecycles();
		}
	}


	@After
	public void tearDown() throws InterruptedException {
		TestUtils.deleteTestDirectory(testDir);
	}

	private class SharerListener implements EventListener {

		private volatile boolean requestReceived = false;
		private volatile boolean responseReceived = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				State s = event.getState();
				if ((s == DELIVERED || s == INVALID) && !event.isLocal()) {
					LOG.info("TEST: Sharer received message");
					msgWaiter.resume();
				}
			} else if (e instanceof ForumInvitationResponseReceivedEvent) {
				ForumInvitationResponseReceivedEvent event =
						(ForumInvitationResponseReceivedEvent) e;
				eventWaiter.assertEquals(contactId1, event.getContactId());
				responseReceived = true;
				eventWaiter.resume();
			}
			// this is only needed for tests where a forum is re-shared
			else if (e instanceof ForumInvitationReceivedEvent) {
				ForumInvitationReceivedEvent event =
						(ForumInvitationReceivedEvent) e;
				eventWaiter.assertEquals(contactId1, event.getContactId());
				requestReceived = true;
				Forum f = event.getForum();
				try {
					Contact c = contactManager0.getContact(contactId1);
					forumSharingManager0.respondToInvitation(f, c, true);
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
		}
	}

	private class InviteeListener implements EventListener {

		private volatile boolean requestReceived = false;
		private volatile boolean responseReceived = false;

		private final boolean accept, answer;

		private InviteeListener(boolean accept, boolean answer) {
			this.accept = accept;
			this.answer = answer;
		}

		private InviteeListener(boolean accept) {
			this(accept, true);
		}

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				State s = event.getState();
				if ((s == DELIVERED || s == INVALID) && !event.isLocal()) {
					LOG.info("TEST: Invitee received message");
					msgWaiter.resume();
				}
			} else if (e instanceof ForumInvitationReceivedEvent) {
				ForumInvitationReceivedEvent event =
						(ForumInvitationReceivedEvent) e;
				requestReceived = true;
				if (!answer) return;
				Forum f = event.getForum();
				try {
					eventWaiter.assertEquals(1,
							forumSharingManager1.getInvitations().size());
					InvitationItem invitation =
							forumSharingManager1.getInvitations().iterator()
									.next();
					eventWaiter.assertEquals(f, invitation.getShareable());
				if (respond) {
					Contact c =
							contactManager1.getContact(event.getContactId());
					forumSharingManager1.respondToInvitation(f, c, accept);
				}
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
			// this is only needed for tests where a forum is re-shared
			else if (e instanceof ForumInvitationResponseReceivedEvent) {
				ForumInvitationResponseReceivedEvent event =
						(ForumInvitationResponseReceivedEvent) e;
				eventWaiter.assertEquals(contactId0, event.getContactId());
				responseReceived = true;
				eventWaiter.resume();
			}
		}
	}

	private void startLifecycles() throws InterruptedException {
		// Start the lifecycle manager and wait for it to finish
		lifecycleManager0 = t0.getLifecycleManager();
		lifecycleManager1 = t1.getLifecycleManager();
		lifecycleManager2 = t2.getLifecycleManager();
		lifecycleManager0.startServices();
		lifecycleManager1.startServices();
		lifecycleManager2.startServices();
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

	private void defaultInit(boolean accept) throws DbException {
		addDefaultIdentities();
		addDefaultContacts();
		addForumForSharer();
		listenToEvents(accept);
	}

	private void addDefaultIdentities() throws DbException {
		KeyPair keyPair = cryptoComponent.generateSignatureKeyPair();
		author0 = authorFactory.createLocalAuthor(SHARER,
				keyPair.getPublic().getEncoded(),
				keyPair.getPrivate().getEncoded());
		identityManager0.addLocalAuthor(author0);

		keyPair = cryptoComponent.generateSignatureKeyPair();
		author1 = authorFactory.createLocalAuthor(INVITEE,
				keyPair.getPublic().getEncoded(),
				keyPair.getPrivate().getEncoded());
		identityManager1.addLocalAuthor(author1);

		keyPair = cryptoComponent.generateSignatureKeyPair();
		author2 = authorFactory.createLocalAuthor(SHARER2,
				keyPair.getPublic().getEncoded(),
				keyPair.getPrivate().getEncoded());
		identityManager2.addLocalAuthor(author2);
	}

	private void addDefaultContacts() throws DbException {
		// sharer adds invitee as contact
		contactId1 = contactManager0.addContact(author1,
				author0.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		// second sharer does the same
		contactId21 = contactManager2.addContact(author1,
				author2.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		// invitee adds sharers back
		contactId0 = contactManager1.addContact(author0,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		contactId2 = contactManager1.addContact(author2,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
	}

	private void addForumForSharer() throws DbException {
		// sharer creates forum
		forum0 = forumManager0.addForum("Test Forum");
	}

	private void listenToEvents(boolean accept) {
		listener0 = new SharerListener();
		t0.getEventBus().addListener(listener0);
		listener1 = new InviteeListener(accept);
		t1.getEventBus().addListener(listener1);
		listener2 = new SharerListener();
		t2.getEventBus().addListener(listener2);
	}

	private void syncToInvitee() throws IOException, TimeoutException {
		deliverMessage(sync0, contactId0, sync1, contactId1, 1,
				"Sharer to Invitee");
	}

	private void syncToSharer() throws IOException, TimeoutException {
		deliverMessage(sync1, contactId1, sync0, contactId0, 1,
				"Invitee to Sharer");
	}

	private void deliverMessage(SyncSessionFactory fromSync, ContactId fromId,
			SyncSessionFactory toSync, ContactId toId, int num, String debug)
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

		// wait for message to actually arrive
		msgWaiter.await(TIMEOUT, num);
	}

	private void injectEagerSingletons(
			ForumSharingIntegrationTestComponent component) {

		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new ForumModule.EagerSingletons());
		component.inject(new CryptoModule.EagerSingletons());
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new SharingModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new PropertiesModule.EagerSingletons());
	}

}
