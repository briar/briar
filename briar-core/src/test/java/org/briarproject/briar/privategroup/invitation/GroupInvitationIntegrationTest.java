package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.DeletionResult;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationItem;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import static java.util.Collections.emptySet;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.test.BriarTestUtils.assertGroupCount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GroupInvitationIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private PrivateGroup privateGroup;
	private PrivateGroupManager groupManager0, groupManager1;
	private GroupInvitationManager groupInvitationManager0,
			groupInvitationManager1;
	private Group g1From0, g0From1;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		groupManager0 = c0.getPrivateGroupManager();
		groupManager1 = c1.getPrivateGroupManager();
		groupInvitationManager0 = c0.getGroupInvitationManager();
		groupInvitationManager1 = c1.getGroupInvitationManager();
		g1From0 = groupInvitationManager0.getContactGroup(contact1From0);
		g0From1 = groupInvitationManager1.getContactGroup(contact0From1);

		privateGroup =
				privateGroupFactory.createPrivateGroup("Testgroup", author0);
		long joinTime = clock.currentTimeMillis();
		GroupMessage joinMsg0 = groupMessageFactory
				.createJoinMessage(privateGroup.getId(), joinTime, author0);
		groupManager0.addPrivateGroup(privateGroup, joinMsg0, true);
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

	@Test
	public void testSendInvitation() throws Exception {
		long timestamp = clock.currentTimeMillis();
		String text = "Hi!";
		sendInvitation(timestamp, text);

		sync0To1(1, true);

		Collection<GroupInvitationItem> invitations =
				groupInvitationManager1.getInvitations();
		assertEquals(1, invitations.size());
		GroupInvitationItem item = invitations.iterator().next();
		assertEquals(contact0From1, item.getCreator());
		assertEquals(privateGroup, item.getShareable());
		assertEquals(privateGroup.getId(), item.getId());
		assertEquals(privateGroup.getName(), item.getName());
		assertFalse(item.isSubscribed());

		Collection<ConversationMessageHeader> messages = getMessages0From1();
		assertEquals(1, messages.size());
		GroupInvitationRequest request =
				(GroupInvitationRequest) messages.iterator().next();
		assertEquals(text, request.getText());
		assertEquals(author0, request.getNameable().getCreator());
		assertEquals(timestamp, request.getTimestamp());
		assertEquals(privateGroup.getName(), request.getNameable().getName());
		assertFalse(request.isLocal());
		assertFalse(request.isRead());
		assertFalse(request.canBeOpened());
		assertFalse(request.wasAnswered());
	}

	@Test
	public void testInvitationDecline() throws Exception {
		long timestamp = clock.currentTimeMillis();
		sendInvitation(timestamp, null);

		sync0To1(1, true);
		assertFalse(groupInvitationManager1.getInvitations().isEmpty());

		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);

		Collection<ConversationMessageHeader> messages = getMessages0From1();
		assertEquals(2, messages.size());
		boolean foundResponse = false;
		for (ConversationMessageHeader m : messages) {
			if (m instanceof GroupInvitationResponse) {
				foundResponse = true;
				GroupInvitationResponse response = (GroupInvitationResponse) m;
				assertEquals(privateGroup.getId(), response.getShareableId());
				assertTrue(response.isLocal());
				assertFalse(response.wasAccepted());
			}
		}
		assertTrue(foundResponse);

		sync1To0(1, true);

		messages = getMessages1From0();
		assertEquals(2, messages.size());
		foundResponse = false;
		for (ConversationMessageHeader m : messages) {
			if (m instanceof GroupInvitationResponse) {
				foundResponse = true;
				GroupInvitationResponse response = (GroupInvitationResponse) m;
				assertEquals(privateGroup.getId(), response.getShareableId());
				assertFalse(response.isLocal());
				assertFalse(response.wasAccepted());
			}
		}
		assertTrue(foundResponse);

		// no invitations are open
		assertTrue(groupInvitationManager1.getInvitations().isEmpty());
		// no groups were added
		assertEquals(0, groupManager1.getPrivateGroups().size());
	}

	@Test
	public void testInvitationDeclineWithAutoDelete() throws Exception {
		// 0 and 1 set an auto-delete timer for their conversation
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		setAutoDeleteTimer(c1, contactId0From1, MIN_AUTO_DELETE_TIMER_MS);

		// Send invitation
		sendInvitation(clock.currentTimeMillis(), null);
		sync0To1(1, true);

		// Decline invitation
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);
		sync1To0(1, true);

		// Group was not added
		assertTrue(groupManager1.getPrivateGroups().isEmpty());

		// All visible messages between 0 and 1 should have auto-delete timers
		for (ConversationMessageHeader h : getMessages1From0()) {
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		}
		for (ConversationMessageHeader h : getMessages0From1()) {
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		}
	}

	@Test
	public void testInvitationAccept() throws Exception {
		long timestamp = clock.currentTimeMillis();
		sendInvitation(timestamp, null);

		// check that invitation message state is correct
		Collection<ConversationMessageHeader> messages = getMessages1From0();
		assertEquals(1, messages.size());
		assertMessageState(messages.iterator().next(), true, false, false);

		sync0To1(1, true);
		assertFalse(groupInvitationManager1.getInvitations().isEmpty());

		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);

		messages = getMessages0From1();
		assertEquals(2, messages.size());
		boolean foundResponse = false;
		for (ConversationMessageHeader m : messages) {
			if (m instanceof GroupInvitationResponse) {
				foundResponse = true;
				GroupInvitationResponse response = (GroupInvitationResponse) m;
				assertMessageState(response, true, false, false);
				assertEquals(privateGroup.getId(), response.getShareableId());
				assertTrue(response.wasAccepted());
			} else {
				GroupInvitationRequest request = (GroupInvitationRequest) m;
				assertEquals(privateGroup, request.getNameable());
				assertTrue(request.wasAnswered());
				assertTrue(request.canBeOpened());
			}
		}
		assertTrue(foundResponse);

		sync1To0(1, true);

		messages = getMessages1From0();
		assertEquals(2, messages.size());
		foundResponse = false;
		for (ConversationMessageHeader m : messages) {
			if (m instanceof GroupInvitationResponse) {
				foundResponse = true;
				GroupInvitationResponse response = (GroupInvitationResponse) m;
				assertEquals(privateGroup.getId(), response.getShareableId());
				assertTrue(response.wasAccepted());
			}
		}
		assertTrue(foundResponse);

		// no invitations are open
		assertTrue(groupInvitationManager1.getInvitations().isEmpty());
		// group was added
		Collection<PrivateGroup> groups = groupManager1.getPrivateGroups();
		assertEquals(1, groups.size());
		assertEquals(privateGroup, groups.iterator().next());
	}

	@Test
	public void testInvitationAcceptWithAutoDelete() throws Exception {
		// 0 and 1 set an auto-delete timer for their conversation
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		setAutoDeleteTimer(c1, contactId0From1, MIN_AUTO_DELETE_TIMER_MS);

		// Send invitation
		sendInvitation(clock.currentTimeMillis(), null);
		sync0To1(1, true);

		// Accept invitation
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);
		sync1To0(1, true);

		// Group was added
		Collection<PrivateGroup> groups = groupManager1.getPrivateGroups();
		assertEquals(1, groups.size());
		assertEquals(privateGroup, groups.iterator().next());

		// All visible messages between 0 and 1 should have auto-delete timers
		for (ConversationMessageHeader h : getMessages1From0()) {
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		}
		for (ConversationMessageHeader h : getMessages0From1()) {
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		}
	}

	@Test
	public void testGroupCount() throws Exception {
		long timestamp = clock.currentTimeMillis();
		sendInvitation(timestamp, null);

		// 0 has one read outgoing message
		Group g1 = groupInvitationManager0.getContactGroup(contact1From0);
		assertGroupCount(messageTracker0, g1.getId(), 1, 0, timestamp);

		sync0To1(1, true);

		// 1 has one unread message
		Group g0 = groupInvitationManager1.getContactGroup(contact0From1);
		assertGroupCount(messageTracker1, g0.getId(), 1, 1, timestamp);
		ConversationMessageHeader m = getMessages0From1().iterator().next();

		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);

		// 1 has two messages, one still unread
		assertGroupCount(messageTracker1, g0.getId(), 2, 1);

		// now all messages should be read
		groupInvitationManager1.setReadFlag(g0.getId(), m.getId(), true);
		assertGroupCount(messageTracker1, g0.getId(), 2, 0);

		sync1To0(1, true);

		// now 0 has two messages, one of them unread
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);
	}

	@Test
	public void testMultipleInvitations() throws Exception {
		sendInvitation(clock.currentTimeMillis(), null);

		// invitation is not allowed before the first hasn't been answered
		assertFalse(groupInvitationManager0
				.isInvitationAllowed(contact1From0, privateGroup.getId()));

		// deliver invitation and response
		sync0To1(1, true);
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);
		sync1To0(1, true);

		// after invitation was declined, inviting again is possible
		assertTrue(groupInvitationManager0
				.isInvitationAllowed(contact1From0, privateGroup.getId()));

		// send and accept the second invitation
		sendInvitation(clock.currentTimeMillis(), "Second Invitation");
		sync0To1(1, true);
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);
		sync1To0(1, true);

		// invitation is not allowed since the member joined the group now
		assertFalse(groupInvitationManager0
				.isInvitationAllowed(contact1From0, privateGroup.getId()));

		// don't allow another invitation request
		try {
			sendInvitation(clock.currentTimeMillis(), "Third Invitation");
			fail();
		} catch (ProtocolStateException e) {
			// expected
		}
	}

	@Test(expected = ProtocolStateException.class)
	public void testInvitationsWithSameTimestamp() throws Exception {
		long timestamp = clock.currentTimeMillis();
		sendInvitation(timestamp, null);
		sync0To1(1, true);

		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);
		sync1To0(1, true);

		sendInvitation(timestamp, "Second Invitation");
		sync0To1(1, true);

		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);
	}

	@Test(expected = ProtocolStateException.class)
	public void testCreatorLeavesBeforeInvitationAccepted() throws Exception {
		// Creator invites invitee to join group
		sendInvitation(clock.currentTimeMillis(), null);

		// Creator's invite message is delivered to invitee
		sync0To1(1, true);

		// Creator leaves group
		assertEquals(1, groupManager0.getPrivateGroups().size());
		groupManager0.removePrivateGroup(privateGroup.getId());
		assertEquals(0, groupManager0.getPrivateGroups().size());

		// Creator's leave message is delivered to invitee
		sync0To1(1, true);

		// Invitee accepts invitation, but it's no longer open - exception is
		// thrown as the action has failed
		assertEquals(0, groupManager1.getPrivateGroups().size());
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);
	}

	@Test
	public void testCreatorLeavesBeforeInvitationDeclined() throws Exception {
		// Creator invites invitee to join group
		sendInvitation(clock.currentTimeMillis(), null);

		// Creator's invite message is delivered to invitee
		sync0To1(1, true);

		// Creator leaves group
		assertEquals(1, groupManager0.getPrivateGroups().size());
		groupManager0.removePrivateGroup(privateGroup.getId());
		assertEquals(0, groupManager0.getPrivateGroups().size());

		// Creator's leave message is delivered to invitee
		sync0To1(1, true);

		// invitee should have no more open invitations
		assertTrue(groupInvitationManager1.getInvitations().isEmpty());

		// Invitee declines invitation, but it's no longer open - no exception
		// as the action has succeeded
		assertEquals(0, groupManager1.getPrivateGroups().size());
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);
	}

	@Test
	public void testCreatorLeavesConcurrentlyWithInvitationAccepted()
			throws Exception {
		// Creator invites invitee to join group
		sendInvitation(clock.currentTimeMillis(), null);

		// Creator's invite message is delivered to invitee
		sync0To1(1, true);

		// Creator leaves group
		assertEquals(1, groupManager0.getPrivateGroups().size());
		groupManager0.removePrivateGroup(privateGroup.getId());
		assertEquals(0, groupManager0.getPrivateGroups().size());

		// Invitee accepts invitation
		assertEquals(0, groupManager1.getPrivateGroups().size());
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);
		assertEquals(1, groupManager1.getPrivateGroups().size());
		assertFalse(groupManager1.isDissolved(privateGroup.getId()));

		// Invitee's join message is delivered to creator
		sync1To0(1, true);

		// Creator's leave message is delivered to invitee
		sync0To1(1, true);

		// Group is marked as dissolved
		assertTrue(groupManager1.isDissolved(privateGroup.getId()));
	}

	@Test
	public void testCreatorLeavesConcurrentlyWithInvitationDeclined()
			throws Exception {
		// Creator invites invitee to join group
		sendInvitation(clock.currentTimeMillis(), null);

		// Creator's invite message is delivered to invitee
		sync0To1(1, true);

		// Creator leaves group
		assertEquals(1, groupManager0.getPrivateGroups().size());
		groupManager0.removePrivateGroup(privateGroup.getId());
		assertEquals(0, groupManager0.getPrivateGroups().size());

		// Invitee declines invitation
		assertEquals(0, groupManager1.getPrivateGroups().size());
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);
		assertEquals(0, groupManager1.getPrivateGroups().size());

		// Invitee's leave message is delivered to creator
		sync1To0(1, true);

		// Creator's leave message is delivered to invitee
		sync0To1(1, true);
	}

	@Test
	public void testCreatorLeavesConcurrentlyWithMemberLeaving()
			throws Exception {
		// Creator invites invitee to join group
		sendInvitation(clock.currentTimeMillis(), null);

		// Creator's invite message is delivered to invitee
		sync0To1(1, true);

		// Invitee responds to invitation
		assertEquals(0, groupManager1.getPrivateGroups().size());
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);
		assertEquals(1, groupManager1.getPrivateGroups().size());

		// Invitee's (sharing) join message is delivered to creator
		sync1To0(1, true);

		// Creator's (sharing and group) join messages are delivered to invitee
		sync0To1(2, true);

		// Invitee's (group) join message is delivered to creator
		sync1To0(1, true);

		// Creator leaves group
		assertEquals(1, groupManager0.getPrivateGroups().size());
		groupManager0.removePrivateGroup(privateGroup.getId());
		assertEquals(0, groupManager0.getPrivateGroups().size());

		// Invitee leaves group
		groupManager1.removePrivateGroup(privateGroup.getId());
		assertEquals(0, groupManager1.getPrivateGroups().size());

		// Creator's leave message is delivered to invitee
		sync0To1(1, true);

		// Invitee's leave message is delivered to creator
		sync1To0(1, true);
	}

	@Test
	public void testDeletingAllMessagesWhenCompletingSession()
			throws Exception {
		// send invitation
		sendInvitation(clock.currentTimeMillis(), null);
		sync0To1(1, true);

		// messages can not be deleted
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertTrue(deleteAllMessages1From0().hasInvitationSessionInProgress());
		assertEquals(1, getMessages1From0().size());
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(deleteAllMessages0From1().hasInvitationSessionInProgress());
		assertEquals(1, getMessages0From1().size());

		// respond
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, true);
		sync1To0(1, true);

		// check group count
		assertGroupCount(messageTracker0, g1From0.getId(), 2, 1);
		assertGroupCount(messageTracker1, g0From1.getId(), 2, 1);

		// messages can be deleted now by creator, invitee needs to wait for ACK
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());
		assertTrue(deleteAllMessages1From0()
				.allDeleted());  // a second time nothing happens
		assertGroupCount(messageTracker0, g1From0.getId(), 0, 0);

		// trying to delete fails for invitee
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(deleteAllMessages0From1().hasInvitationSessionInProgress());
		assertEquals(2, getMessages0From1().size());

		// creator sends two JOIN messages (one sharing + one in private group)
		// this includes the ACK for response
		sync0To1(2, true);

		// now invitee can also delete messages
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertEquals(0, getMessages0From1().size());
		// a second time nothing happens
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertGroupCount(messageTracker1, g0From1.getId(), 0, 0);

		// invitee now leaves
		groupManager1.removePrivateGroup(privateGroup.getId());
		sync1To0(1, true);

		// no new messages to delete
		assertEquals(0, getMessages1From0().size());
		assertEquals(0, getMessages0From1().size());
	}

	@Test
	public void testDeletingAllMessagesWhenDeclining() throws Exception {
		// send invitation
		sendInvitation(clock.currentTimeMillis(), null);
		sync0To1(1, true);

		// respond
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);
		sync1To0(1, true);

		// check group count
		Group g1From0 = groupInvitationManager0.getContactGroup(contact1From0);
		Group g0From1 = groupInvitationManager1.getContactGroup(contact0From1);
		assertGroupCount(messageTracker0, g1From0.getId(), 2, 1);
		assertGroupCount(messageTracker1, g0From1.getId(), 2, 1);

		// messages can be deleted now by creator, invitee needs to wait for ACK
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());
		// a second time nothing happens
		assertTrue(deleteAllMessages1From0().allDeleted());

		// trying to delete fails for invitee
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(deleteAllMessages0From1().hasInvitationSessionInProgress());
		assertEquals(2, getMessages0From1().size());

		// creator sends ACK
		sendAcks(c0, c1, contactId1From0, 1);

		// now invitee can also delete messages
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertEquals(0, getMessages0From1().size());
		// a second time nothing happens
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertGroupCount(messageTracker1, g0From1.getId(), 0, 0);

		// creator can re-invite
		sendInvitation(clock.currentTimeMillis(), null);
		sync0To1(1, true);

		// now new messages can not be deleted anymore
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertTrue(deleteAllMessages1From0().hasInvitationSessionInProgress());
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(deleteAllMessages0From1().hasInvitationSessionInProgress());

		// responding again
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);
		sync1To0(1, true);

		// creator sends ACK
		sendAcks(c0, c1, contactId1From0, 1);

		// asserting group counts
		assertGroupCount(messageTracker1, g0From1.getId(), 2, 1);
		assertGroupCount(messageTracker0, g1From0.getId(), 2, 1);

		// deleting is possible again
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertGroupCount(messageTracker1, g0From1.getId(), 0, 0);
		assertGroupCount(messageTracker0, g1From0.getId(), 0, 0);
	}

	@Test
	public void testDeletingSomeMessages() throws Exception {
		// send invitation
		sendInvitation(clock.currentTimeMillis(), null);
		sync0To1(1, true);

		// deleting the invitation will fail for both
		Collection<ConversationMessageHeader> m0 = getMessages1From0();
		assertEquals(1, m0.size());
		MessageId messageId = m0.iterator().next().getId();
		Set<MessageId> toDelete = new HashSet<>();
		toDelete.add(messageId);
		assertFalse(deleteMessages1From0(toDelete).allDeleted());
		assertTrue(deleteMessages1From0(toDelete)
				.hasInvitationSessionInProgress());
		assertFalse(deleteMessages0From1(toDelete).allDeleted());
		assertTrue(deleteMessages0From1(toDelete)
				.hasInvitationSessionInProgress());

		// respond
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup, false);
		sync1To0(1, true);

		// both can still not delete the invitation,
		// because the response was not selected for deletion as well
		assertFalse(deleteMessages1From0(toDelete).allDeleted());
		assertTrue(
				deleteMessages1From0(toDelete).hasNotAllInvitationSelected());
		assertFalse(deleteMessages0From1(toDelete).allDeleted());
		assertTrue(
				deleteMessages0From1(toDelete).hasNotAllInvitationSelected());

		// after selecting response, both messages can be deleted by creator
		m0 = getMessages1From0();
		assertEquals(2, m0.size());
		for (ConversationMessageHeader h : m0) {
			if (!h.getId().equals(messageId)) toDelete.add(h.getId());
		}
		assertGroupCount(messageTracker0, g1From0.getId(), 2, 1);
		assertTrue(deleteMessages1From0(toDelete).allDeleted());
		assertEquals(0, getMessages1From0().size());
		// a second time nothing happens
		assertTrue(deleteMessages1From0(toDelete).allDeleted());
		assertGroupCount(messageTracker0, g1From0.getId(), 0, 0);

		// 1 can still not delete the messages, as last one has not been ACKed
		assertFalse(deleteMessages0From1(toDelete).allDeleted());
		assertTrue(deleteMessages0From1(toDelete)
				.hasInvitationSessionInProgress());
		assertEquals(2, getMessages0From1().size());
		assertGroupCount(messageTracker1, g0From1.getId(), 2, 1);

		// 0 sends an ACK to their last message
		sendAcks(c0, c1, contactId1From0, 1);

		// 1 can now delete all messages, as last one has been ACKed
		assertTrue(deleteMessages0From1(toDelete).allDeleted());
		assertEquals(0, getMessages0From1().size());
		assertGroupCount(messageTracker1, g0From1.getId(), 0, 0);
		// a second time nothing happens
		assertTrue(deleteMessages0From1(toDelete).allDeleted());
	}

	@Test
	public void testDeletingEmptySet() throws Exception {
		assertTrue(deleteMessages0From1(emptySet()).allDeleted());
	}

	private Collection<ConversationMessageHeader> getMessages1From0()
			throws DbException {
		return db0.transactionWithResult(true, txn -> groupInvitationManager0
				.getMessageHeaders(txn, contactId1From0));
	}

	private Collection<ConversationMessageHeader> getMessages0From1()
			throws DbException {
		return db1.transactionWithResult(true, txn -> groupInvitationManager1
				.getMessageHeaders(txn, contactId0From1));
	}

	private DeletionResult deleteAllMessages1From0() throws DbException {
		return db0.transactionWithResult(false, txn -> groupInvitationManager0
				.deleteAllMessages(txn, contactId1From0));
	}

	private DeletionResult deleteAllMessages0From1() throws DbException {
		return db1.transactionWithResult(false, txn -> groupInvitationManager1
				.deleteAllMessages(txn, contactId0From1));
	}

	private DeletionResult deleteMessages1From0(Set<MessageId> toDelete)
			throws DbException {
		return db0.transactionWithResult(false, txn -> groupInvitationManager0
				.deleteMessages(txn, contactId1From0, toDelete));
	}

	private DeletionResult deleteMessages0From1(Set<MessageId> toDelete)
			throws DbException {
		return db1.transactionWithResult(false, txn -> groupInvitationManager1
				.deleteMessages(txn, contactId0From1, toDelete));
	}

	private void sendInvitation(long timestamp, @Nullable String text)
			throws DbException {
		byte[] signature = groupInvitationFactory.signInvitation(contact1From0,
				privateGroup.getId(), timestamp, author0.getPrivateKey());
		long timer = getAutoDeleteTimer(c0, contactId1From0, timestamp);
		groupInvitationManager0.sendInvitation(privateGroup.getId(),
				contactId1From0, text, timestamp, signature, timer);
	}

}
