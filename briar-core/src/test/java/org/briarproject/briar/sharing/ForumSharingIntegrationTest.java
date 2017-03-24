package org.briarproject.briar.sharing;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseModule;
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
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static org.briarproject.bramble.test.TestUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForumSharingIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private ForumManager forumManager0, forumManager1;
	private MessageEncoder messageEncoder;
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
		messageEncoder = new MessageEncoderImpl(clientHelper, messageFactory);

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
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

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
				assertTrue(invitation.canBeOpened());
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
				.sendInvitation(forum0.getId(), contactId1From0, null,
						clock.currentTimeMillis());

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
				assertFalse(invitation.canBeOpened());
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
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

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
		assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
				.contains(contact1From0));
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
				.contains(contact1From0));
		// invitee no longer gets forum shared by sharer
		assertFalse(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0));
		// forum can be shared again by sharer
		assertTrue(forumSharingManager0
				.canBeShared(forum0.getId(), contact1From0));
		// invitee that left can not share again
		assertFalse(forumSharingManager1
				.canBeShared(forum0.getId(), contact0From1));
	}

	@Test
	public void testSharerLeavesAfterFinished() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, null,
						clock.currentTimeMillis());

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
				.sendInvitation(forum0.getId(), contactId1From0, null,
						clock.currentTimeMillis());

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
				.sendInvitation(forum0.getId(), contactId1From0, null,
						clock.currentTimeMillis());

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

	@Test(expected = IllegalArgumentException.class)
	public void testSharingSameForumWithEachOther() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

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

		// invitee now shares same forum back
		forumSharingManager1.sendInvitation(forum0.getId(),
				contactId0From1,
				"I am re-sharing this forum with you.",
				clock.currentTimeMillis());
	}

	@Test
	public void testSharingSameForumWithEachOtherAtSameTime() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// invitee adds the same forum
		Transaction txn = db1.startTransaction(false);
		forumManager1.addForum(txn, forum0);
		db1.commitTransaction(txn);
		db1.endTransaction(txn);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// invitee now shares same forum back
		forumSharingManager1.sendInvitation(forum0.getId(),
				contactId0From1, "I am re-sharing this forum with you.",
				clock.currentTimeMillis());

		// prevent automatic responses
		respond = false;

		// only now sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync second invitation which counts as accept
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.requestReceived);

		// both peers should share the forum with each other now
		assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0From1));

		// and both have each other's invitations (and no response)
		assertEquals(2, forumSharingManager0
				.getInvitationMessages(contactId1From0).size());
		assertEquals(2, forumSharingManager1
				.getInvitationMessages(contactId0From1).size());

		// there are no more open invitations
		assertTrue(forumSharingManager0.getInvitations().isEmpty());
		assertTrue(forumSharingManager1.getInvitations().isEmpty());
	}

	@Test
	public void testContactRemoved() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

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

		// contacts now remove each other
		removeAllContacts();

		// invitee still has forum
		assertEquals(1, forumManager1.getForums().size());

		// make sure sharer does share the forum with nobody now
		assertEquals(0,
				forumSharingManager0.getSharedWith(forum0.getId()).size());

		// contacts add each other again
		addDefaultContacts();
		addContacts1And2();

		// forum can be shared with contacts again
		assertTrue(forumSharingManager0
				.canBeShared(forum0.getId(), contact1From0));
		assertTrue(forumSharingManager0
				.canBeShared(forum0.getId(), contact2From0));
		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum is still there
		assertEquals(1, forumManager1.getForums().size());
		assertEquals(1,
				forumSharingManager0.getSharedWith(forum0.getId()).size());
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
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());
		// sync first request message
		sync0To1(1, true);

		// second sharer sends invitation for same forum
		assertTrue(contactId1From2 != null);
		forumSharingManager2
				.sendInvitation(forum0.getId(), contactId1From2, null,
						clock.currentTimeMillis());
		// sync second request message
		sync2To1(1, true);

		// make sure we now have two invitations to the same forum available
		Collection<SharingInvitationItem> forums =
				forumSharingManager1.getInvitations();
		assertEquals(1, forums.size());
		assertEquals(2, forums.iterator().next().getNewSharers().size());
		assertEquals(forum0, forums.iterator().next().getShareable());

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

		// make sure both sharers actually share the forum
		Collection<Contact> contacts =
				forumSharingManager1.getSharedWith(forum0.getId());
		assertEquals(2, contacts.size());
	}

	@Test
	public void testSyncAfterReSharing() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

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
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

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

	@Test
	public void testSessionResetAfterAbort() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// get invitation MessageId for later
		MessageId invitationId = null;
		for (InvitationMessage m : forumSharingManager1
				.getInvitationMessages(contactId0From1)) {
			if (m instanceof ForumInvitationRequest) {
				invitationId = m.getId();
			}
		}
		assertNotNull(invitationId);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// forum is shared mutually
		assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0From1));

		// send an accept message for the same forum
		Message m = messageEncoder.encodeAcceptMessage(
				forumSharingManager0.getContactGroup(contact1From0).getId(),
				forum0.getId(), clock.currentTimeMillis(), invitationId);
		c0.getClientHelper().addLocalMessage(m, new BdfDictionary(), true);

		// sync unexpected message and the expected abort message back
		sync0To1(1, true);
		sync1To0(1, true);

		// forum is no longer shared mutually
		assertFalse(forumSharingManager0.getSharedWith(forum0.getId())
				.contains(contact1From0));
		assertFalse(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0From1));

		// new invitation is possible now
		forumSharingManager0
				.sendInvitation(forum0.getId(), contactId1From0, null,
						clock.currentTimeMillis());
		sync0To1(1, true);

		// and can be answered
		sync1To0(1, true);

		// forum is shared mutually again
		assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum0.getId())
				.contains(contact0From1));
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
					if (respond) {
						Contact c = contactManager0.getContact(contactId1From0);
						forumSharingManager0.respondToInvitation(f, c, true);
					}
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
					if (respond) {
						eventWaiter.assertEquals(1,
								forumSharingManager1.getInvitations().size());
						SharingInvitationItem invitation =
								forumSharingManager1.getInvitations().iterator()
										.next();
						eventWaiter.assertEquals(f, invitation.getShareable());
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
