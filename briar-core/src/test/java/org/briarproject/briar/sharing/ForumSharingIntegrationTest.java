package org.briarproject.briar.sharing;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.TestDatabaseModule;
import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.briar.BriarIntegrationTest;
import org.briarproject.briar.BriarIntegrationTestComponent;
import org.briarproject.briar.DaggerBriarIntegrationTestComponent;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.forum.ForumPostHeader;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.forum.event.ForumInvitationRequestReceivedEvent;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.briar.api.sharing.InvitationMessage;
import org.briarproject.briar.api.sharing.SharingInvitationItem;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static org.briarproject.TestUtils.getRandomBytes;
import static org.briarproject.TestUtils.getRandomString;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.briar.api.forum.ForumSharingManager.CLIENT_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForumSharingIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private ForumManager forumManager0, forumManager1;
	private SharerListener listener0, listener2;
	private InviteeListener listener1;
	private Forum forum0;

	// objects accessed from background threads need to be volatile
	private volatile ForumSharingManager forumSharingManager0;
	private volatile ForumSharingManager forumSharingManager1;
	private volatile ForumSharingManager forumSharingManager2;
	private volatile Waiter eventWaiter;

	private boolean respond = true;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		forumManager0 = c0.getForumManager();
		forumManager1 = c1.getForumManager();
		forumSharingManager0 = c0.getForumSharingManager();
		forumSharingManager1 = c1.getForumSharingManager();
		forumSharingManager2 = c2.getForumSharingManager();

		// initialize waiter fresh for each test
		eventWaiter = new Waiter();

		addContacts1And2();
		addForumForSharer();
	}

	@Override
	protected void createComponents() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		component.inject(this);

		c0 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(c0);

		c1 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(c1);

		c2 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t2Dir)).build();
		injectEagerSingletons(c2);
	}

	private void addForumForSharer() throws DbException {
		forum0 = forumManager0.addForum("Test Forum");
	}

	@Test
	public void testSuccessfulSharing() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());

		// invitee has one invitation message from sharer
		List<InvitationMessage> list =
				new ArrayList<InvitationMessage>(forumSharingManager1
						.getInvitationMessages(contactId0From1));
		assertEquals(2, list.size());
		// check other things are alright with the forum message
		for (InvitationMessage m : list) {
			if (m instanceof ForumInvitationRequest) {
				ForumInvitationRequest invitation =
						(ForumInvitationRequest) m;
				assertFalse(invitation.isAvailable());
				assertEquals(forum0.getName(), invitation.getForumName());
				assertEquals(contactId1From0, invitation.getContactId());
				assertEquals("Hi!", invitation.getMessage());
			} else {
				ForumInvitationResponse response =
						(ForumInvitationResponse) m;
				assertEquals(contactId0From1, response.getContactId());
				assertTrue(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2,
				forumSharingManager0.getInvitationMessages(contactId1From0)
						.size());
		// forum can not be shared again
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertFalse(forumSharingManager0.canBeShared(forum0.getId(), c1));
		Contact c0 = contactManager1.getContact(contactId0From1);
		assertFalse(forumSharingManager1.canBeShared(forum0.getId(), c0));
	}

	@Test
	public void testDeclinedSharing() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(false);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, null);

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum was not added
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(0, forumManager1.getForums().size());
		// forum is no longer available to invitee who declined
		assertEquals(0, forumSharingManager1.getInvitations().size());

		// invitee has one invitation message from sharer and one response
		List<InvitationMessage> list =
				new ArrayList<InvitationMessage>(forumSharingManager1
						.getInvitationMessages(contactId0From1));
		assertEquals(2, list.size());
		// check things are alright with the forum message
		for (InvitationMessage m : list) {
			if (m instanceof ForumInvitationRequest) {
				ForumInvitationRequest invitation =
						(ForumInvitationRequest) m;
				assertFalse(invitation.isAvailable());
				assertEquals(forum0.getName(), invitation.getForumName());
				assertEquals(contactId1From0, invitation.getContactId());
				assertEquals(null, invitation.getMessage());
			} else {
				ForumInvitationResponse response =
						(ForumInvitationResponse) m;
				assertEquals(contactId0From1, response.getContactId());
				assertFalse(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2,
				forumSharingManager0.getInvitationMessages(contactId1From0)
						.size());
		// forum can be shared again
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertTrue(forumSharingManager0.canBeShared(forum0.getId(), c1));
	}

	@Test
	public void testInviteeLeavesAfterFinished() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
		assertTrue(forumManager1.getForums().contains(forum0));

		// sharer shares forum with invitee
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
				.contains(c1));
		// invitee gets forum shared by sharer
		Contact contact0 = contactManager1.getContact(contactId1From0);
		assertTrue(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0));

		// invitee un-subscribes from forum
		forumManager1.removeForum(forum0);

		// send leave message to sharer
		sync1To0(1, true);

		// forum is gone
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(0, forumManager1.getForums().size());

		// sharer no longer shares forum with invitee
		assertFalse(forumSharingManager0.getSharedWith(forum0.getId())
				.contains(c1));
		// invitee no longer gets forum shared by sharer
		assertFalse(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0));
		// forum can be shared again
		assertTrue(forumSharingManager0.canBeShared(forum0.getId(), c1));
		Contact c0 = contactManager1.getContact(contactId0From1);
		assertTrue(forumSharingManager1.canBeShared(forum0.getId(), c0));
	}

	@Test
	public void testSharerLeavesAfterFinished() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, null);

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
		assertTrue(forumManager1.getForums().contains(forum0));

		// sharer shares forum with invitee
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
				.contains(c1));
		// invitee gets forum shared by sharer
		Contact contact0 = contactManager1.getContact(contactId1From0);
		assertTrue(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0));

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum0);

		// send leave message to invitee
		sync0To1(1, true);

		// forum is gone for sharer, but not invitee
		assertEquals(0, forumManager0.getForums().size());
		assertEquals(1, forumManager1.getForums().size());

		// invitee no longer shares forum with sharer
		Contact c0 = contactManager1.getContact(contactId0From1);
		assertFalse(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(c0));
		// sharer no longer gets forum shared by invitee
		assertFalse(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0));
		// forum can be shared again
		assertTrue(forumSharingManager1.canBeShared(forum0.getId(), c0));
	}

	@Test
	public void testSharerLeavesBeforeResponse() throws Exception {
		// initialize except event listeners
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, null);

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum0);

		// prevent invitee response before syncing messages
		respond = false;

		// sync first request message and leave message
		sync0To1(2, true);

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
				.sendInvitation(forum0.getId(), contactId1From0, null);

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum0);

		// sync first request message and leave message
		sync0To1(2, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// ensure that invitee has no forum invitations available
		assertEquals(0, forumSharingManager1.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
	}

	@Test
	public void testSessionIdReuse() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum was added successfully
		assertEquals(1, forumManager1.getForums().size());

		// reset event received state
		listener1.requestReceived = false;

		// get SessionId from invitation
		List<InvitationMessage> list = new ArrayList<InvitationMessage>(
				forumSharingManager1
						.getInvitationMessages(contactId0From1));
		assertEquals(2, list.size());
		InvitationMessage msg = list.get(0);
		SessionId sessionId = msg.getSessionId();
		assertEquals(sessionId, list.get(1).getSessionId());

		// get all sorts of stuff needed to send a message
		MessageQueueManager queue = c0.getMessageQueueManager();
		Contact c1 = contactManager0.getContact(contactId1From0);
		Group group = contactGroupFactory.createContactGroup(CLIENT_ID, c1);
		long time = clock.currentTimeMillis();
		BdfList bodyList =
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId.getBytes(),
						getRandomString(42), getRandomBytes(FORUM_SALT_LENGTH));
		byte[] body = clientHelper.toByteArray(bodyList);

		// add the message to the queue
		Transaction txn = db0.startTransaction(false);
		try {
			queue.sendMessage(txn, group, time, body, new Metadata());
			db0.commitTransaction(txn);
		} finally {
			db0.endTransaction(txn);
		}

		// actually send the message
		sync0To1(1, false);
		// make sure there was no new request received
		assertFalse(listener1.requestReceived);
	}

	@Test
	public void testSharingSameForumWithEachOther() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum was added successfully
		assertEquals(1, forumManager1.getForums().size());
		assertEquals(2,
				forumSharingManager0.getInvitationMessages(contactId1From0)
						.size());

		// invitee now shares same forum back
		forumSharingManager1.sendInvitation(forum0.getId(),
				contactId0From1,
				"I am re-sharing this forum with you.");

		// sync re-share invitation
		sync1To0(1, false);

		// make sure that no new request was received
		assertFalse(listener0.requestReceived);
		assertEquals(2,
				forumSharingManager0.getInvitationMessages(contactId1From0)
						.size());
		assertEquals(0, forumSharingManager0.getInvitations().size());
	}

	@Test
	public void testSharingSameForumWithEachOtherAtSameTime() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// invitee adds the same forum
		Transaction txn = db1.startTransaction(false);
		db1.addGroup(txn, forum0.getGroup());
		db1.commitTransaction(txn);
		db1.endTransaction(txn);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");

		// invitee now shares same forum back
		forumSharingManager1.sendInvitation(forum0.getId(),
				contactId0From1, "I am re-sharing this forum with you.");

		// find out who should be Alice, because of random keys
		Bytes key0 = new Bytes(author0.getPublicKey());
		Bytes key1 = new Bytes(author1.getPublicKey());

		// only now sync first request message
		boolean alice = key1.compareTo(key0) < 0;
		if (alice) {
			sync0To1(1, false);
			assertFalse(listener1.requestReceived);
		} else {
			sync0To1(1, true);
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);
		}

		// sync second invitation
		alice = key0.compareTo(key1) < 0;
		if (alice) {
			sync1To0(1, false);
			assertFalse(listener0.requestReceived);

			// sharer did not receive request, but response to own request
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			assertEquals(2, forumSharingManager0
					.getInvitationMessages(contactId1From0).size());
			assertEquals(3, forumSharingManager1
					.getInvitationMessages(contactId0From1).size());
		} else {
			sync1To0(1, true);
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.requestReceived);

			// send response from sharer to invitee and make sure it arrived
			sync0To1(1, true);
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.responseReceived);

			assertEquals(3, forumSharingManager0
					.getInvitationMessages(contactId1From0).size());
			assertEquals(2, forumSharingManager1
					.getInvitationMessages(contactId0From1).size());
		}
	}

	@Test
	public void testContactRemoved() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum was added successfully
		assertEquals(1, forumManager1.getForums().size());
		assertEquals(1,
				forumSharingManager0.getSharedWith(forum0.getId()).size());

		// remember SessionId from invitation
		List<InvitationMessage> list = new ArrayList<InvitationMessage>(
				forumSharingManager1
						.getInvitationMessages(contactId0From1));
		assertEquals(2, list.size());
		InvitationMessage msg = list.get(0);
		SessionId sessionId = msg.getSessionId();
		assertEquals(sessionId, list.get(1).getSessionId());

		// contacts now remove each other
		removeAllContacts();

		// make sure sharer does share the forum with nobody now
		assertEquals(0,
				forumSharingManager0.getSharedWith(forum0.getId()).size());

		// contacts add each other again
		addDefaultContacts();
		addContacts1And2();

		// get all sorts of stuff needed to send a message
		MessageQueueManager queue = c0.getMessageQueueManager();
		Contact c1 = contactManager0.getContact(contactId1From0);
		Group group = contactGroupFactory.createContactGroup(CLIENT_ID, c1);
		long time = clock.currentTimeMillis();

		// construct a new message re-using the old SessionId
		BdfList bodyList = BdfList.of(SHARE_MSG_TYPE_INVITATION,
				sessionId.getBytes(),
				getRandomString(42),
				getRandomBytes(FORUM_SALT_LENGTH)
		);
		byte[] body = clientHelper.toByteArray(bodyList);

		// add the message to the queue
		Transaction txn = db0.startTransaction(false);
		try {
			queue.sendMessage(txn, group, time, body, new Metadata());
			db0.commitTransaction(txn);
		} finally {
			db0.endTransaction(txn);
		}

		// actually send the message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		// make sure the new request was received with the same sessionId
		// as proof that the state got deleted along with contacts
		assertTrue(listener1.requestReceived);
	}

	@Test
	public void testTwoContactsShareSameForum() throws Exception {
		// second sharer adds the same forum
		Transaction txn = db2.startTransaction(false);
		db2.addGroup(txn, forum0.getGroup());
		db2.commitTransaction(txn);
		db2.endTransaction(txn);

		// add listeners
		listener0 = new SharerListener();
		c0.getEventBus().addListener(listener0);
		listener1 = new InviteeListener(true, false);
		c1.getEventBus().addListener(listener1);
		listener2 = new SharerListener();
		c2.getEventBus().addListener(listener2);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");
		// sync first request message
		sync0To1(1, true);

		// second sharer sends invitation for same forum
		assertTrue(contactId1From2 != null);
		forumSharingManager2
				.sendInvitation(forum0.getId(), contactId1From2, null);
		// sync second request message
		sync2To1(1, true);

		// make sure we now have two invitations to the same forum available
		Collection<SharingInvitationItem> forums =
				forumSharingManager1.getInvitations();
		assertEquals(1, forums.size());
		assertEquals(2, forums.iterator().next().getNewSharers().size());
		assertEquals(forum0, forums.iterator().next().getShareable());
		assertEquals(2,
				forumSharingManager1.getSharedWith(forum0.getId()).size());

		// make sure both sharers actually share the forum
		Collection<Contact> contacts =
				forumSharingManager1.getSharedWith(forum0.getId());
		assertEquals(2, contacts.size());

		// answer second request
		assertNotNull(contactId2From1);
		Contact contact2From1 = contactManager1.getContact(contactId2From1);
		forumSharingManager1.respondToInvitation(forum0, contact2From1, true);
		// sync response
		sync1To2(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener2.responseReceived);

		// answer first request
		Contact c0 =
				contactManager1.getContact(contactId0From1);
		forumSharingManager1.respondToInvitation(forum0, c0, true);
		// sync response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);
	}

	@Test
	public void testSyncAfterReSharing() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// sharer posts into the forum
		long time = clock.currentTimeMillis();
		String body = getRandomString(42);
		ForumPost p = forumPostFactory
				.createPost(forum0.getId(), time, null, author0,
						body);
		forumManager0.addLocalPost(p);

		// sync forum post
		sync0To1(1, true);

		// make sure forum post arrived
		Collection<ForumPostHeader> headers =
				forumManager1.getPostHeaders(forum0.getId());
		assertEquals(1, headers.size());
		ForumPostHeader header = headers.iterator().next();
		assertEquals(p.getMessage().getId(), header.getId());
		assertEquals(author0, header.getAuthor());

		// now invitee creates a post
		time = clock.currentTimeMillis();
		body = getRandomString(42);
		p = forumPostFactory
				.createPost(forum0.getId(), time, null, author1,
						body);
		forumManager1.addLocalPost(p);

		// sync forum post
		sync1To0(1, true);

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
		removeAllContacts();

		// contacts add each other back
		addDefaultContacts();
		addContacts1And2();

		// send invitation again
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!");

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// now invitee creates a post
		time = clock.currentTimeMillis();
		body = getRandomString(42);
		p = forumPostFactory
				.createPost(forum0.getId(), time, null, author1,
						body);
		forumManager1.addLocalPost(p);

		// sync forum post
		sync1To0(1, true);

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
	}

	@NotNullByDefault
	private class SharerListener implements EventListener {

		private volatile boolean requestReceived = false;
		private volatile boolean responseReceived = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof ForumInvitationResponseReceivedEvent) {
				responseReceived = true;
				eventWaiter.resume();
			}
			// this is only needed for tests where a forum is re-shared
			else if (e instanceof ForumInvitationRequestReceivedEvent) {
				ForumInvitationRequestReceivedEvent event =
						(ForumInvitationRequestReceivedEvent) e;
				eventWaiter.assertEquals(contactId1From0, event.getContactId());
				requestReceived = true;
				Forum f = event.getShareable();
				try {
					Contact c = contactManager0.getContact(contactId1From0);
					forumSharingManager0.respondToInvitation(f, c, true);
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
		}
	}

	@NotNullByDefault
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
			if (e instanceof ForumInvitationRequestReceivedEvent) {
				ForumInvitationRequestReceivedEvent event =
						(ForumInvitationRequestReceivedEvent) e;
				requestReceived = true;
				if (!answer) return;
				Forum f = event.getShareable();
				try {
					eventWaiter.assertEquals(1,
							forumSharingManager1.getInvitations().size());
					SharingInvitationItem invitation =
							forumSharingManager1.getInvitations().iterator()
									.next();
					eventWaiter.assertEquals(f, invitation.getShareable());
					if (respond) {
						Contact c =
								contactManager1
										.getContact(event.getContactId());
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
				eventWaiter.assertEquals(contactId0From1, event.getContactId());
				responseReceived = true;
				eventWaiter.resume();
			}
		}
	}

	private void listenToEvents(boolean accept) throws DbException {
		listener0 = new SharerListener();
		c0.getEventBus().addListener(listener0);
		listener1 = new InviteeListener(accept);
		c1.getEventBus().addListener(listener1);
		listener2 = new SharerListener();
		c2.getEventBus().addListener(listener2);
	}

}
