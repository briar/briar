package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.privategroup.invitation.PeerState.AWAIT_MEMBER;
import static org.briarproject.briar.privategroup.invitation.PeerState.BOTH_JOINED;
import static org.briarproject.briar.privategroup.invitation.PeerState.ERROR;
import static org.briarproject.briar.privategroup.invitation.PeerState.LOCAL_JOINED;
import static org.briarproject.briar.privategroup.invitation.PeerState.LOCAL_LEFT;
import static org.briarproject.briar.privategroup.invitation.PeerState.NEITHER_JOINED;
import static org.briarproject.briar.privategroup.invitation.PeerState.START;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class PeerProtocolEngineTest extends AbstractProtocolEngineTest {

	private final PeerProtocolEngine engine =
			new PeerProtocolEngine(db, clientHelper, clientVersioningManager,
					privateGroupManager, privateGroupFactory,
					groupMessageFactory, identityManager, messageParser,
					messageEncoder, autoDeleteManager,
					conversationManager, clock);

	private PeerSession getDefaultSession(PeerState state) {
		return new PeerSession(contactGroupId, privateGroupId,
				lastLocalMessageId, lastRemoteMessageId, localTimestamp, state);
	}

	// onInviteAction

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromStart() {
		engine.onInviteAction(txn, getDefaultSession(START), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromAwaitMember() {
		engine.onInviteAction(txn, getDefaultSession(AWAIT_MEMBER), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromNeitherJoined() {
		engine.onInviteAction(txn, getDefaultSession(NEITHER_JOINED), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromLocalJoined() {
		engine.onInviteAction(txn, getDefaultSession(LOCAL_JOINED), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromBothJoined() {
		engine.onInviteAction(txn, getDefaultSession(BOTH_JOINED), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromLocalLeft() {
		engine.onInviteAction(txn, getDefaultSession(LOCAL_LEFT), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromError() {
		engine.onInviteAction(txn, getDefaultSession(ERROR), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	// onJoinAction

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromStart() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(START));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromAwaitMember() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(AWAIT_MEMBER));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromLocalJoined() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(LOCAL_JOINED));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromBothJoined() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(BOTH_JOINED));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromError() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(ERROR));
	}

	@Test
	public void testOnJoinActionFromNeitherJoined() throws Exception {
		JoinMessage joinMessage =
				new JoinMessage(messageId, contactGroupId,
						privateGroupId, messageTimestamp, lastRemoteMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(NEITHER_JOINED);

		expectSendJoinMessage(joinMessage, false);
		expectSetPrivateGroupVisibility(VISIBLE);
		PeerSession newSession = engine.onJoinAction(txn, session);

		assertEquals(LOCAL_JOINED, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnJoinActionFromLocalLeft() throws Exception {
		JoinMessage joinMessage =
				new JoinMessage(messageId, contactGroupId,
						privateGroupId, messageTimestamp, lastRemoteMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(LOCAL_LEFT);

		expectSendJoinMessage(joinMessage, false);
		expectSetPrivateGroupVisibility(SHARED);
		PeerSession newSession = engine.onJoinAction(txn, session);

		assertEquals(BOTH_JOINED, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	// onLeaveAction

	@Test
	public void testOnLeaveActionFromStart() throws Exception {
		PeerSession session = getDefaultSession(START);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromAwaitMember() throws Exception {
		PeerSession session = getDefaultSession(AWAIT_MEMBER);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromNeitherJoined() throws Exception {
		PeerSession session = getDefaultSession(NEITHER_JOINED);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromLocalLeft() throws Exception {
		PeerSession session = getDefaultSession(LOCAL_LEFT);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromError() throws Exception {
		PeerSession session = getDefaultSession(ERROR);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromLocalJoined() throws Exception {
		PeerSession session = getDefaultSession(LOCAL_JOINED);

		expectSendLeaveMessage(false);
		expectSetPrivateGroupVisibility(INVISIBLE);
		PeerSession newSession = engine.onLeaveAction(txn, session, false);

		assertEquals(NEITHER_JOINED, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnLeaveActionFromBothJoined() throws Exception {
		PeerSession session = getDefaultSession(BOTH_JOINED);

		expectSendLeaveMessage(false);
		expectSetPrivateGroupVisibility(INVISIBLE);
		PeerSession newSession = engine.onLeaveAction(txn, session, false);

		assertEquals(LOCAL_LEFT, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	// onMemberAddedAction

	@Test
	public void testOnMemberAddedFromStart() throws Exception {
		PeerSession session = getDefaultSession(START);
		PeerSession newSession = engine.onMemberAddedAction(txn, session);

		assertEquals(NEITHER_JOINED, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(session.getLastRemoteMessageId(),
				newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnMemberAddedFromAwaitMember() throws Exception {
		JoinMessage joinMessage =
				new JoinMessage(messageId, contactGroupId,
						privateGroupId, messageTimestamp, lastRemoteMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(AWAIT_MEMBER);

		expectSendJoinMessage(joinMessage, false);
		expectSetPrivateGroupVisibility(SHARED);
		expectRelationshipRevealed(true);
		PeerSession newSession = engine.onMemberAddedAction(txn, session);

		assertEquals(BOTH_JOINED, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnMemberAddedFromNeitherJoined() throws Exception {
		engine.onMemberAddedAction(txn, getDefaultSession(NEITHER_JOINED));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnMemberAddedFromLocalJoined() throws Exception {
		engine.onMemberAddedAction(txn, getDefaultSession(LOCAL_JOINED));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnMemberAddedFromBothJoined() throws Exception {
		engine.onMemberAddedAction(txn, getDefaultSession(BOTH_JOINED));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnMemberAddedFromLocalLeft() throws Exception {
		engine.onMemberAddedAction(txn, getDefaultSession(LOCAL_LEFT));
	}

	@Test
	public void testOnMemberAddedFromError() throws Exception {
		PeerSession session = getDefaultSession(ERROR);
		assertEquals(session, engine.onMemberAddedAction(txn, session));
	}

	// onInviteMessage

	@Test
	public void testOnInviteMessageFromStart() throws Exception {
		PeerSession session = getDefaultSession(START);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromAwaitMember() throws Exception {
		PeerSession session = getDefaultSession(AWAIT_MEMBER);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromNeitherJoined() throws Exception {
		PeerSession session = getDefaultSession(NEITHER_JOINED);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromLocalJoined() throws Exception {
		PeerSession session = getDefaultSession(LOCAL_JOINED);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromBothJoined() throws Exception {
		PeerSession session = getDefaultSession(BOTH_JOINED);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromLocalLeft() throws Exception {
		PeerSession session = getDefaultSession(LOCAL_LEFT);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromError() throws Exception {
		PeerSession session = getDefaultSession(ERROR);
		assertEquals(session,
				engine.onInviteMessage(txn, session, inviteMessage));
	}

	// onJoinMessage

	@Test
	public void testOnJoinMessageFromAwaitMember() throws Exception {
		PeerSession session = getDefaultSession(AWAIT_MEMBER);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromBothJoined() throws Exception {
		PeerSession session = getDefaultSession(BOTH_JOINED);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromLocalLeft() throws Exception {
		PeerSession session = getDefaultSession(LOCAL_LEFT);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromStartWithInvalidDependency()
			throws Exception {
		JoinMessage invalidJoinMessage =
				new JoinMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, 0L, lastLocalMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(START);
		assertNotNull(invalidJoinMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotEquals(invalidJoinMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		expectAbortWhenNotSubscribedToGroup();
		PeerSession newSession =
				engine.onJoinMessage(txn, session, invalidJoinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromStart() throws Exception {
		PeerSession session = getDefaultSession(START);
		assertNotNull(joinMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertEquals(joinMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		PeerSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);

		assertEquals(AWAIT_MEMBER, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(joinMessage.getId(), newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromNeitherJoinedWithInvalidDependency()
			throws Exception {
		JoinMessage invalidJoinMessage =
				new JoinMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, 0L, lastLocalMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(NEITHER_JOINED);
		assertNotNull(invalidJoinMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotEquals(invalidJoinMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		expectAbortWhenNotSubscribedToGroup();
		PeerSession newSession =
				engine.onJoinMessage(txn, session, invalidJoinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromNeitherJoined() throws Exception {
		PeerSession session = getDefaultSession(NEITHER_JOINED);
		assertNotNull(joinMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertEquals(joinMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());
		JoinMessage myJoinMessage = new JoinMessage(messageId, contactGroupId,
				privateGroupId, messageTimestamp, lastRemoteMessageId,
				NO_AUTO_DELETE_TIMER);

		expectSendJoinMessage(myJoinMessage, false);
		expectSetPrivateGroupVisibility(SHARED);
		expectRelationshipRevealed(true);
		PeerSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);

		assertEquals(BOTH_JOINED, newSession.getState());
		assertEquals(myJoinMessage.getId(), newSession.getLastLocalMessageId());
		assertEquals(joinMessage.getId(), newSession.getLastRemoteMessageId());
		assertEquals(myJoinMessage.getTimestamp(),
				newSession.getLocalTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromLocalJoinedWithInvalidDependency()
			throws Exception {
		JoinMessage invalidJoinMessage =
				new JoinMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, 0L, lastLocalMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(LOCAL_JOINED);
		assertNotNull(invalidJoinMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotEquals(invalidJoinMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		expectAbortWhenNotSubscribedToGroup();
		PeerSession newSession =
				engine.onJoinMessage(txn, session, invalidJoinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromLocalJoined() throws Exception {
		PeerSession session = getDefaultSession(LOCAL_JOINED);
		assertNotNull(joinMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertEquals(joinMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		expectSetPrivateGroupVisibility(SHARED);
		expectRelationshipRevealed(false);
		PeerSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);

		assertEquals(BOTH_JOINED, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(joinMessage.getId(), newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromError() throws Exception {
		PeerSession session = getDefaultSession(ERROR);
		assertEquals(session,
				engine.onJoinMessage(txn, session, joinMessage));
	}

	// onLeaveMessage

	@Test
	public void testOnLeaveMessageFromStart() throws Exception {
		PeerSession session = getDefaultSession(START);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromNeitherJoined() throws Exception {
		PeerSession session = getDefaultSession(NEITHER_JOINED);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromLocalJoined() throws Exception {
		PeerSession session = getDefaultSession(LOCAL_JOINED);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromAwaitMemberWithInvalidDependency()
			throws Exception {
		LeaveMessage invalidLeaveMessage =
				new LeaveMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, 0L, lastLocalMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(AWAIT_MEMBER);
		assertNotNull(invalidLeaveMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotEquals(invalidLeaveMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onLeaveMessage(txn, session, invalidLeaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromAwaitMember() throws Exception {
		PeerSession session = getDefaultSession(AWAIT_MEMBER);
		assertNotNull(leaveMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertEquals(leaveMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		PeerSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);

		assertEquals(START, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(leaveMessage.getId(), newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromLocalLeftWithInvalidDependency()
			throws Exception {
		LeaveMessage invalidLeaveMessage =
				new LeaveMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, 0L, lastLocalMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(LOCAL_LEFT);
		assertNotNull(invalidLeaveMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotEquals(invalidLeaveMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onLeaveMessage(txn, session, invalidLeaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromLocalLeft() throws Exception {
		PeerSession session = getDefaultSession(LOCAL_LEFT);
		assertNotNull(leaveMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertEquals(leaveMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		PeerSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);

		assertEquals(NEITHER_JOINED, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(leaveMessage.getId(), newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromBothJoinedWithInvalidDependency()
			throws Exception {
		LeaveMessage invalidLeaveMessage =
				new LeaveMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, 0L, lastLocalMessageId,
						NO_AUTO_DELETE_TIMER);
		PeerSession session = getDefaultSession(BOTH_JOINED);
		assertNotNull(invalidLeaveMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotEquals(invalidLeaveMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onLeaveMessage(txn, session, invalidLeaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromBothJoined() throws Exception {
		PeerSession session = getDefaultSession(BOTH_JOINED);
		assertNotNull(leaveMessage.getPreviousMessageId());
		assertNotNull(session.getLastRemoteMessageId());
		assertEquals(leaveMessage.getPreviousMessageId(),
				session.getLastRemoteMessageId());

		expectSetPrivateGroupVisibility(VISIBLE); // FIXME correct?
		PeerSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);

		assertEquals(LOCAL_JOINED, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(leaveMessage.getId(), newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromError() throws Exception {
		PeerSession session = getDefaultSession(ERROR);
		assertEquals(session,
				engine.onLeaveMessage(txn, session, leaveMessage));
	}

	// onAbortMessage

	@Test
	public void testOnAbortMessageWhenNotSubscribed() throws Exception {
		PeerSession session = getDefaultSession(START);

		expectAbortWhenSubscribedToGroup();
		PeerSession newSession =
				engine.onAbortMessage(txn, session, abortMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnAbortMessageWhenSubscribed() throws Exception {
		PeerSession session = getDefaultSession(START);

		expectAbortWhenNotSubscribedToGroup();
		PeerSession newSession =
				engine.onAbortMessage(txn, session, abortMessage);
		assertSessionAborted(session, newSession);
	}

	// helper methods

	private void expectRelationshipRevealed(boolean byContact)
			throws Exception {
		expectGetContactId();
		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(privateGroupManager)
					.relationshipRevealed(txn, privateGroupId, author.getId(),
							byContact);
		}});
	}

	private void expectAbortWhenSubscribedToGroup() throws Exception {
		expectIsSubscribedPrivateGroup();
		expectSetPrivateGroupVisibility(INVISIBLE);
		expectSendAbortMessage();
	}

	private void expectAbortWhenNotSubscribedToGroup() throws Exception {
		expectIsNotSubscribedPrivateGroup();
		expectSendAbortMessage();
	}

	private void assertSessionAborted(PeerSession oldSession,
			PeerSession newSession) {
		assertEquals(ERROR, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(oldSession, newSession);
	}

	@Override
	protected void assertSessionRecordedSentMessage(Session<?> s) {
		assertEquals(messageId, s.getLastLocalMessageId());
		assertEquals(lastRemoteMessageId, s.getLastRemoteMessageId());
		assertEquals(messageTimestamp, s.getLocalTimestamp());
		// invitation timestamp is untouched for peers
	}

}
