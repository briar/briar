package org.briarproject.briar.sharing;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.ConversationResponse;
import org.briarproject.briar.api.conversation.DeletionResult;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.forum.ForumPostHeader;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.forum.event.ForumInvitationRequestReceivedEvent;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.briar.api.sharing.SharingInvitationItem;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import static java.util.Collections.emptySet;
import static junit.framework.Assert.assertNotNull;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.forum.ForumSharingManager.CLIENT_ID;
import static org.briarproject.briar.api.forum.ForumSharingManager.MAJOR_VERSION;
import static org.briarproject.briar.test.BriarTestUtils.assertGroupCount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ForumSharingIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private ForumManager forumManager0, forumManager1;
	private MessageEncoder messageEncoder;
	private Listener listener0, listener2, listener1;
	private Forum forum;

	// objects accessed from background threads need to be volatile
	private volatile ForumSharingManager forumSharingManager0;
	private volatile ForumSharingManager forumSharingManager1;
	private volatile ForumSharingManager forumSharingManager2;
	private volatile Waiter eventWaiter;

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

		listener0 = new Listener();
		c0.getEventBus().addListener(listener0);
		listener1 = new Listener();
		c1.getEventBus().addListener(listener1);
		listener2 = new Listener();
		c2.getEventBus().addListener(listener2);

		addContacts1And2();
		addForumForSharer();
	}

	@Override
	protected void createComponents() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(component);
		component.inject(this);

		c0 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t0Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c0);

		c1 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t1Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c1);

		c2 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t2Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c2);
	}

	private void addForumForSharer() throws DbException {
		forum = forumManager0.addForum("Test Forum");
	}

	@Test
	public void testSuccessfulSharing() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// check that request message state is correct
		Collection<ConversationMessageHeader> messages = getMessages1From0();
		assertEquals(1, messages.size());
		assertMessageState(messages.iterator().next(), true, false, false);

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// check that accept message state is correct
		messages = getMessages0From1();
		assertEquals(2, messages.size());
		for (ConversationMessageHeader h : messages) {
			if (h instanceof ConversationResponse) {
				assertMessageState(h, true, false, false);
			}
		}

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());

		// invitee has one invitation message from sharer
		Collection<ConversationMessageHeader> list = getMessages0From1();
		assertEquals(2, list.size());
		// check other things are alright with the forum message
		for (ConversationMessageHeader m : list) {
			if (m instanceof ForumInvitationRequest) {
				ForumInvitationRequest invitation = (ForumInvitationRequest) m;
				assertTrue(invitation.wasAnswered());
				assertEquals(forum.getName(), invitation.getName());
				assertEquals(forum, invitation.getNameable());
				assertEquals("Hi!", invitation.getText());
				assertTrue(invitation.canBeOpened());
			} else {
				ForumInvitationResponse response = (ForumInvitationResponse) m;
				assertEquals(forum.getId(), response.getShareableId());
				assertTrue(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2, getMessages1From0().size());
		// forum can not be shared again
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertFalse(forumSharingManager0.canBeShared(forum.getId(), c1));
		Contact c0 = contactManager1.getContact(contactId0From1);
		assertFalse(forumSharingManager1.canBeShared(forum.getId(), c0));
	}

	@Test
	public void testSuccessfulSharingWithAutoDelete() throws Exception {
		// Set an auto-delete timer for the conversation
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		setAutoDeleteTimer(c1, contactId0From1, MIN_AUTO_DELETE_TIMER_MS);

		// Send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// Sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// Invitee accepts
		respondToRequest(contactId0From1, true);

		// Sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// Forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());

		// All visible messages should have auto-delete timers
		for (ConversationMessageHeader h : getMessages1From0()) {
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		}
		for (ConversationMessageHeader h : getMessages0From1()) {
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		}
	}

	@Test
	public void testDeclinedSharing() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee declines
		respondToRequest(contactId0From1, false);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, false);

		// forum was not added
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(0, forumManager1.getForums().size());
		// forum is no longer available to invitee who declined
		assertEquals(0, forumSharingManager1.getInvitations().size());

		// invitee has one invitation message from sharer and one response
		Collection<ConversationMessageHeader> list = getMessages0From1();
		assertEquals(2, list.size());
		// check things are alright with the forum message
		for (ConversationMessageHeader m : list) {
			if (m instanceof ForumInvitationRequest) {
				ForumInvitationRequest invitation = (ForumInvitationRequest) m;
				assertEquals(forum, invitation.getNameable());
				assertTrue(invitation.wasAnswered());
				assertEquals(forum.getName(), invitation.getName());
				assertNull(invitation.getText());
				assertFalse(invitation.canBeOpened());
			} else {
				ForumInvitationResponse response = (ForumInvitationResponse) m;
				assertEquals(forum.getId(), response.getShareableId());
				assertFalse(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2, getMessages1From0().size());
		// forum can be shared again
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertTrue(forumSharingManager0.canBeShared(forum.getId(), c1));

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum);

		// send a new invitation again after re-adding the forum
		db0.transaction(false, txn -> forumManager0.addForum(txn, forum));
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);

		// reset listener state for new request
		listener1.requestReceived = false;
		listener1.requestContactId = null;

		// sync only 1 request message to make sure there wasn't an abort
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);
	}

	@Test
	public void testInviteeLeavesAfterFinished() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
		assertTrue(forumManager1.getForums().contains(forum));

		// sharer shares forum with invitee
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		// invitee gets forum shared by sharer
		Contact contact0 = contactManager1.getContact(contactId1From0);
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0));

		// invitee un-subscribes from forum
		forumManager1.removeForum(forum);

		// send leave message to sharer
		sync1To0(1, true);

		// forum is gone
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(0, forumManager1.getForums().size());

		// sharer no longer shares forum with invitee
		assertFalse(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		// invitee no longer gets forum shared by sharer
		assertFalse(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0));
		// forum can be shared again by sharer
		assertTrue(forumSharingManager0
				.canBeShared(forum.getId(), contact1From0));
		// invitee that left can not yet share again
		assertFalse(forumSharingManager1
				.canBeShared(forum.getId(), contact0From1));

		// sharer responds with leave message
		sync0To1(1, true);

		// invitee that left can now share again
		assertTrue(forumSharingManager1
				.canBeShared(forum.getId(), contact0From1));
	}

	@Test
	public void testSharerLeavesAfterFinished() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum was added successfully
		assertEquals(0, forumSharingManager0.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
		assertTrue(forumManager1.getForums().contains(forum));

		// sharer shares forum with invitee
		Contact c1 = contactManager0.getContact(contactId1From0);
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(c1));
		// invitee gets forum shared by sharer
		Contact contact0 = contactManager1.getContact(contactId1From0);
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0));

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum);

		// send leave message to invitee
		sync0To1(1, true);

		// forum is gone for sharer, but not invitee
		assertEquals(0, forumManager0.getForums().size());
		assertEquals(1, forumManager1.getForums().size());

		// invitee no longer shares forum with sharer
		Contact c0 = contactManager1.getContact(contactId0From1);
		assertFalse(forumSharingManager1.getSharedWith(forum.getId())
				.contains(c0));
		// sharer no longer gets forum shared by invitee
		assertFalse(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0));
		// forum can be re-shared by invitee now
		assertTrue(forumSharingManager1.canBeShared(forum.getId(), c0));

		// invitee responds with LEAVE message
		sync1To0(1, true);

		// sharer can share forum again as well now
		assertTrue(forumSharingManager0.canBeShared(forum.getId(), c1));

		// invitee also un-subscribes forum without effect
		forumManager1.removeForum(forum);

		// send a new invitation again after re-adding the forum
		db0.transaction(false, txn -> forumManager0.addForum(txn, forum));
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);

		// reset listener state for new request
		listener1.requestReceived = false;
		listener1.requestContactId = null;

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);
	}

	@Test
	public void testSharerLeavesBeforeResponse() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum);

		// sync request message and leave message
		sync0To1(2, true);

		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// ensure that invitee has no forum invitations available
		assertEquals(0, forumSharingManager1.getInvitations().size());
		assertEquals(0, forumManager1.getForums().size());

		// Try again, this time with a response before the leave
		addForumForSharer();

		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sharer un-subscribes from forum
		forumManager0.removeForum(forum);

		// sync leave message
		sync0To1(1, true);

		// ensure that invitee has no forum invitations available
		assertEquals(0, forumSharingManager1.getInvitations().size());
		assertEquals(1, forumManager1.getForums().size());
	}

	@Test
	public void testSharingSameForumWithEachOther() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// response and invitation got tracked
		Group group = contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, contact0From1);
		assertEquals(2, c1.getMessageTracker().getGroupCount(group.getId())
				.getMsgCount());

		// forum was added successfully
		assertEquals(1, forumManager1.getForums().size());

		// invitee now shares same forum back
		forumSharingManager1
				.sendInvitation(forum.getId(), contactId0From1,
						"I am re-sharing this forum with you.");

		// assert that the last invitation wasn't send
		assertEquals(2, c1.getMessageTracker().getGroupCount(group.getId())
				.getMsgCount());
	}

	@Test
	public void testSharingSameForumWithEachOtherBeforeAccept()
			throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// ensure that invitee has received the invitations
		assertEquals(1, forumSharingManager1.getInvitations().size());

		// assert that the invitation arrived
		Group group = contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, contact0From1);
		assertEquals(1, c1.getMessageTracker().getGroupCount(group.getId())
				.getMsgCount());

		// invitee now shares same forum back
		forumSharingManager1
				.sendInvitation(forum.getId(), contactId0From1,
						"I am re-sharing this forum with you.");

		// assert that the last invitation wasn't send
		assertEquals(1, c1.getMessageTracker().getGroupCount(group.getId())
				.getMsgCount());
	}

	@Test
	public void testSharingSameForumWithEachOtherAtSameTime() throws Exception {
		// invitee adds the same forum
		db1.transaction(false, txn -> forumManager1.addForum(txn, forum));

		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// invitee now shares same forum back
		forumSharingManager1
				.sendInvitation(forum.getId(), contactId0From1,
						"I am re-sharing this forum with you.");

		// only now sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// sync second invitation which counts as accept
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener0, contactId1From0);

		// both peers should share the forum with each other now
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0From1));

		// and both have each other's invitations (and no response)
		assertEquals(2, getMessages1From0().size());
		assertEquals(2, getMessages0From1().size());

		// there are no more open invitations
		assertTrue(forumSharingManager0.getInvitations().isEmpty());
		assertTrue(forumSharingManager1.getInvitations().isEmpty());
	}

	@Test
	public void testContactRemoved() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum was added successfully
		assertEquals(1, forumManager1.getForums().size());
		assertEquals(1,
				forumSharingManager0.getSharedWith(forum.getId()).size());

		// contacts now remove each other
		removeAllContacts();

		// invitee still has forum
		assertEquals(1, forumManager1.getForums().size());

		// make sure sharer does share the forum with nobody now
		assertEquals(0,
				forumSharingManager0.getSharedWith(forum.getId()).size());

		// contacts add each other again
		addDefaultContacts();
		addContacts1And2();

		// forum can be shared with contacts again
		assertTrue(forumSharingManager0
				.canBeShared(forum.getId(), contact1From0));
		assertTrue(forumSharingManager0
				.canBeShared(forum.getId(), contact2From0));

		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum is still there
		assertEquals(1, forumManager1.getForums().size());
		assertEquals(1,
				forumSharingManager0.getSharedWith(forum.getId()).size());
	}

	@Test
	public void testTwoContactsShareSameForum() throws Exception {
		// second sharer adds the same forum
		db2.transaction(false, txn -> db2.addGroup(txn, forum.getGroup()));

		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// second sharer sends invitation for same forum
		assertNotNull(contactId1From2);
		forumSharingManager2
				.sendInvitation(forum.getId(), contactId1From2, null);

		// sync second request message
		sync2To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId2From1);

		// make sure we now have two invitations to the same forum available
		Collection<SharingInvitationItem> forums =
				forumSharingManager1.getInvitations();
		assertEquals(1, forums.size());
		assertEquals(2, forums.iterator().next().getNewSharers().size());
		assertEquals(forum, forums.iterator().next().getShareable());

		// answer second request
		assertNotNull(contactId2From1);
		Contact contact2From1 = contactManager1.getContact(contactId2From1);
		forumSharingManager1.respondToInvitation(forum, contact2From1, true);

		// sync response
		sync1To2(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener2, contactId1From2, true);

		// answer first request
		Contact contact0From1 = contactManager1.getContact(contactId0From1);
		forumSharingManager1.respondToInvitation(forum, contact0From1, true);

		// sync response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// make sure both sharers actually share the forum
		Collection<Contact> contacts =
				forumSharingManager1.getSharedWith(forum.getId());
		assertEquals(2, contacts.size());
	}

	@Test
	public void testSyncAfterReSharing() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// sharer posts into the forum
		long time = clock.currentTimeMillis();
		String text = getRandomString(42);
		ForumPost p = forumPostFactory
				.createPost(forum.getId(), time, null, author0, text);
		forumManager0.addLocalPost(p);

		// sync forum post
		sync0To1(1, true);

		// make sure forum post arrived
		Collection<ForumPostHeader> headers =
				forumManager1.getPostHeaders(forum.getId());
		assertEquals(1, headers.size());
		ForumPostHeader header = headers.iterator().next();
		assertEquals(p.getMessage().getId(), header.getId());
		assertEquals(author0, header.getAuthor());

		// now invitee creates a post
		time = clock.currentTimeMillis();
		text = getRandomString(42);
		p = forumPostFactory
				.createPost(forum.getId(), time, null, author1, text);
		forumManager1.addLocalPost(p);

		// sync forum post
		sync1To0(1, true);

		// make sure forum post arrived
		headers = forumManager1.getPostHeaders(forum.getId());
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
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// now invitee creates a post
		time = clock.currentTimeMillis();
		text = getRandomString(42);
		p = forumPostFactory
				.createPost(forum.getId(), time, null, author1, text);
		forumManager1.addLocalPost(p);

		// sync forum post
		sync1To0(1, true);

		// make sure forum post arrived
		headers = forumManager1.getPostHeaders(forum.getId());
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
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, "Hi!");

		// sync request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// get invitation MessageId for later
		MessageId invitationId = null;
		for (ConversationMessageHeader m : getMessages0From1()) {
			if (m instanceof ForumInvitationRequest) {
				invitationId = m.getId();
			}
		}
		assertNotNull(invitationId);

		// invitee accepts
		respondToRequest(contactId0From1, true);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum is shared mutually
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0From1));

		// send an accept message for the same forum
		Message m = messageEncoder.encodeAcceptMessage(
				forumSharingManager0.getContactGroup(contact1From0).getId(),
				forum.getId(), clock.currentTimeMillis(), invitationId);
		c0.getClientHelper().addLocalMessage(m, new BdfDictionary(), true);

		// sync unexpected message and the expected abort message back
		sync0To1(1, true);
		sync1To0(1, true);

		// forum is no longer shared mutually
		assertFalse(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		assertFalse(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0From1));

		// new invitation is possible now
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertRequestReceived(listener1, contactId0From1);

		// and can be answered
		respondToRequest(contactId0From1, true);
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertResponseReceived(listener0, contactId1From0, true);

		// forum is shared mutually again
		assertTrue(forumSharingManager0.getSharedWith(forum.getId())
				.contains(contact1From0));
		assertTrue(forumSharingManager1.getSharedWith(forum.getId())
				.contains(contact0From1));
	}

	@Test
	public void testDeletingAllMessagesWhenCompletingSession()
			throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// messages can not be deleted
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertTrue(deleteAllMessages1From0().hasInvitationSessionInProgress());
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(deleteAllMessages0From1().hasInvitationSessionInProgress());

		// accept invitation
		respondToRequest(contactId0From1, true);
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// check that messages are tracked properly
		GroupId g1From0 =
				forumSharingManager0.getContactGroup(contact1From0).getId();
		GroupId g0From1 =
				forumSharingManager1.getContactGroup(contact0From1).getId();
		assertGroupCount(messageTracker0, g1From0, 2, 1);
		assertGroupCount(messageTracker1, g0From1, 2, 1);

		// 0 deletes all messages
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());
		assertGroupCount(messageTracker0, g1From0, 0, 0);

		// 1 can not delete all messages, as last one has not been ACKed
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(deleteAllMessages0From1().hasInvitationSessionInProgress());
		assertGroupCount(messageTracker1, g0From1, 2, 1);

		// 0 sends an ACK to their last message
		sendAcks(c0, c1, contactId1From0, 1);

		// 1 can now delete all messages, as last one has been ACKed
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertEquals(0, getMessages0From1().size());
		assertGroupCount(messageTracker1, g0From1, 0, 0);

		// sharer leaves forum and sends LEAVE message
		forumManager0.removeForum(forum);
		sync0To1(1, true);

		// invitee responds with LEAVE message
		sync1To0(1, true);

		// sending invitation is possible again
		forumSharingManager1
				.sendInvitation(forum.getId(), contactId0From1, null);
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// messages can not be deleted anymore
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertTrue(deleteAllMessages1From0().hasInvitationSessionInProgress());
		assertEquals(1, getMessages1From0().size());
		assertGroupCount(messageTracker0, g1From0, 1, 1);
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(deleteAllMessages0From1().hasInvitationSessionInProgress());
		assertEquals(1, getMessages0From1().size());
		assertGroupCount(messageTracker1, g0From1, 1, 0);

		// 0 accepts re-share
		forumSharingManager0.respondToInvitation(forum, contact1From0, true);
		sync0To1(1, true);

		// 1 sends an ACK to their last message
		sendAcks(c1, c0, contactId0From1, 1);

		// messages can now get deleted again
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());
		assertGroupCount(messageTracker0, g1From0, 0, 0);
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertEquals(0, getMessages0From1().size());
		assertGroupCount(messageTracker1, g0From1, 0, 0);
	}

	@Test
	public void testDeletingAllMessagesAfterDecline()
			throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// decline invitation
		respondToRequest(contactId0From1, false);
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// 0 deletes all messages
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());

		// 1 can not delete all messages, as last one has not been ACKed
		assertFalse(deleteAllMessages0From1().allDeleted());

		// 0 sends an ACK to their last message
		sendAcks(c0, c1, contactId1From0, 1);

		// 1 can now delete all messages, as last one has been ACKed
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertEquals(0, getMessages0From1().size());

		// re-sending invitation is possible
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// messages can not be deleted anymore
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertEquals(1, getMessages1From0().size());
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertEquals(1, getMessages0From1().size());
	}

	@Test
	public void testDeletingSomeMessages() throws Exception {
		// send invitation
		forumSharingManager0
				.sendInvitation(forum.getId(), contactId1From0, null);
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// deleting the invitation will fail
		Collection<ConversationMessageHeader> m0 = getMessages1From0();
		assertEquals(1, m0.size());
		MessageId messageId = m0.iterator().next().getId();
		Set<MessageId> toDelete = new HashSet<>();
		toDelete.add(messageId);
		assertFalse(deleteMessages1From0(toDelete).allDeleted());
		assertTrue(deleteMessages1From0(toDelete)
				.hasInvitationSessionInProgress());

		// decline invitation
		respondToRequest(contactId0From1, true);
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// both can still not delete the invitation,
		// because the response was not selected for deletion as well
		assertFalse(deleteMessages1From0(toDelete).allDeleted());
		assertTrue(
				deleteMessages1From0(toDelete).hasNotAllInvitationSelected());
		assertFalse(deleteMessages0From1(toDelete).allDeleted());
		assertTrue(
				deleteMessages0From1(toDelete).hasNotAllInvitationSelected());

		// after selecting response, both messages can be deleted
		m0 = getMessages1From0();
		assertEquals(2, m0.size());
		for (ConversationMessageHeader h : m0) {
			if (!h.getId().equals(messageId)) toDelete.add(h.getId());
		}
		assertTrue(deleteMessages1From0(toDelete).allDeleted());
		assertEquals(0, getMessages1From0().size());
		// a second time nothing happens
		assertTrue(deleteMessages1From0(toDelete).allDeleted());

		// 1 can still not delete the messages, as last one has not been ACKed
		assertFalse(deleteMessages0From1(toDelete).allDeleted());
		assertFalse(
				deleteMessages0From1(toDelete).hasNotAllInvitationSelected());
		assertTrue(deleteMessages0From1(toDelete)
				.hasInvitationSessionInProgress());

		// 0 sends an ACK to their last message
		sendAcks(c0, c1, contactId1From0, 1);

		// 1 can now delete all messages, as last one has been ACKed
		assertTrue(deleteMessages0From1(toDelete).allDeleted());
		assertEquals(0, getMessages0From1().size());
		// a second time nothing happens
		assertTrue(deleteMessages0From1(toDelete).allDeleted());
	}

	@Test
	public void testDeletingEmptySet() throws Exception {
		assertTrue(deleteMessages0From1(emptySet()).allDeleted());
	}

	private Collection<ConversationMessageHeader> getMessages1From0()
			throws DbException {
		return db0.transactionWithResult(true, txn -> forumSharingManager0
				.getMessageHeaders(txn, contactId1From0));
	}

	private Collection<ConversationMessageHeader> getMessages0From1()
			throws DbException {
		return db1.transactionWithResult(true, txn -> forumSharingManager1
				.getMessageHeaders(txn, contactId0From1));
	}

	private DeletionResult deleteAllMessages1From0() throws DbException {
		return db0.transactionWithResult(false, txn -> forumSharingManager0
				.deleteAllMessages(txn, contactId1From0));
	}

	private DeletionResult deleteAllMessages0From1() throws DbException {
		return db1.transactionWithResult(false, txn -> forumSharingManager1
				.deleteAllMessages(txn, contactId0From1));
	}

	private DeletionResult deleteMessages1From0(Set<MessageId> toDelete)
			throws DbException {
		return db0.transactionWithResult(false, txn -> forumSharingManager0
				.deleteMessages(txn, contactId1From0, toDelete));
	}

	private DeletionResult deleteMessages0From1(Set<MessageId> toDelete)
			throws DbException {
		return db1.transactionWithResult(false, txn -> forumSharingManager1
				.deleteMessages(txn, contactId0From1, toDelete));
	}

	private void respondToRequest(ContactId contactId, boolean accept)
			throws DbException {
		assertEquals(1, forumSharingManager1.getInvitations().size());
		SharingInvitationItem invitation =
				forumSharingManager1.getInvitations().iterator().next();
		assertEquals(forum, invitation.getShareable());
		Contact c = contactManager1.getContact(contactId);
		forumSharingManager1.respondToInvitation(forum, c, accept);
	}

	private void assertRequestReceived(Listener listener, ContactId contactId) {
		assertTrue(listener.requestReceived);
		assertEquals(contactId, listener.requestContactId);
		listener.reset();
	}

	private void assertResponseReceived(Listener listener, ContactId contactId,
			boolean accept) {
		assertTrue(listener.responseReceived);
		assertEquals(contactId, listener.responseContactId);
		assertEquals(accept, listener.responseAccepted);
		listener.reset();
	}

	@NotNullByDefault
	private class Listener implements EventListener {

		private volatile boolean requestReceived = false;
		@Nullable
		private volatile ContactId requestContactId = null;

		private volatile boolean responseReceived = false;
		@Nullable
		private volatile ContactId responseContactId = null;
		private volatile boolean responseAccepted = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof ForumInvitationRequestReceivedEvent) {
				ForumInvitationRequestReceivedEvent event =
						(ForumInvitationRequestReceivedEvent) e;
				requestReceived = true;
				requestContactId = event.getContactId();
				eventWaiter.resume();
			} else if (e instanceof ForumInvitationResponseReceivedEvent) {
				ForumInvitationResponseReceivedEvent event =
						(ForumInvitationResponseReceivedEvent) e;
				responseReceived = true;
				responseContactId = event.getContactId();
				responseAccepted = event.getMessageHeader().wasAccepted();
				eventWaiter.resume();
			}
		}

		private void reset() {
			requestReceived = responseReceived = responseAccepted = false;
			requestContactId = responseContactId = null;
		}
	}
}
