package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.Collection;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_INVITATION_TEXT_LENGTH;
import static org.briarproject.briar.privategroup.invitation.InviteeState.ACCEPTED;
import static org.briarproject.briar.privategroup.invitation.InviteeState.DISSOLVED;
import static org.briarproject.briar.privategroup.invitation.InviteeState.ERROR;
import static org.briarproject.briar.privategroup.invitation.InviteeState.INVITED;
import static org.briarproject.briar.privategroup.invitation.InviteeState.JOINED;
import static org.briarproject.briar.privategroup.invitation.InviteeState.LEFT;
import static org.briarproject.briar.privategroup.invitation.InviteeState.START;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InviteeProtocolEngineTest extends AbstractProtocolEngineTest {

	private final InviteeProtocolEngine engine =
			new InviteeProtocolEngine(db, clientHelper, clientVersioningManager,
					privateGroupManager, privateGroupFactory,
					groupMessageFactory, identityManager, messageParser,
					messageEncoder, autoDeleteManager,
					conversationManager, clock);
	private final LocalAuthor localAuthor = getLocalAuthor();

	private InviteeSession getDefaultSession(InviteeState state) {
		return new InviteeSession(contactGroupId, privateGroupId,
				lastLocalMessageId, lastRemoteMessageId, localTimestamp,
				inviteTimestamp, state);
	}

	// onInviteAction

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromStart() {
		engine.onInviteAction(txn, getDefaultSession(START), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromLeft() {
		engine.onInviteAction(txn, getDefaultSession(ACCEPTED), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromInvited() {
		engine.onInviteAction(txn, getDefaultSession(INVITED), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromDissolved() {
		engine.onInviteAction(txn, getDefaultSession(DISSOLVED), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromAccepted() {
		engine.onInviteAction(txn, getDefaultSession(ACCEPTED), null,
				messageTimestamp, signature, NO_AUTO_DELETE_TIMER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testOnInviteActionFromJoined() {
		engine.onInviteAction(txn, getDefaultSession(JOINED), null,
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
	public void testOnJoinActionFromAccepted() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(ACCEPTED));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromJoined() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(JOINED));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromLeft() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(LEFT));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromDissolved() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(DISSOLVED));
	}

	@Test(expected = ProtocolStateException.class)
	public void testOnJoinActionFromError() throws Exception {
		engine.onJoinAction(txn, getDefaultSession(ERROR));
	}

	@Test
	public void testOnJoinActionFromInvited() throws Exception {
		JoinMessage properJoinMessage =
				new JoinMessage(messageId, contactGroupId, privateGroupId,
						messageTimestamp, lastRemoteMessageId,
						NO_AUTO_DELETE_TIMER);
		long timestamp = 0L;
		GroupMessage joinGroupMessage =
				new GroupMessage(message, null, localAuthor);
		BdfDictionary meta = new BdfDictionary();

		expectMarkMessageAvailableToAnswer(lastRemoteMessageId, false);
		context.checking(new Expectations() {{
			oneOf(messageEncoder).setInvitationAccepted(meta, true);
			oneOf(clientHelper)
					.mergeMessageMetadata(txn, lastRemoteMessageId, meta);
		}});
		expectSendJoinMessage(properJoinMessage, true);
		context.checking(new Expectations() {{
			oneOf(conversationManager).trackOutgoingMessage(txn, message);
			oneOf(messageParser).getInviteMessage(txn, lastRemoteMessageId);
			will(returnValue(inviteMessage));
			oneOf(privateGroupFactory)
					.createPrivateGroup(inviteMessage.getGroupName(),
							inviteMessage.getCreator(),
							inviteMessage.getSalt());
			will(returnValue(privateGroup));
			oneOf(clock).currentTimeMillis();
			will((returnValue(timestamp)));
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(groupMessageFactory).createJoinMessage(privateGroupId,
					inviteMessage.getTimestamp() + 1, localAuthor,
					inviteMessage.getTimestamp(), inviteMessage.getSignature());
			will(returnValue(joinGroupMessage));
			oneOf(privateGroupManager)
					.addPrivateGroup(txn, privateGroup, joinGroupMessage,
							false);
		}});
		expectSetPrivateGroupVisibility(VISIBLE);

		InviteeSession session = getDefaultSession(INVITED);
		InviteeSession newSession = engine.onJoinAction(txn, session);

		assertEquals(ACCEPTED, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test(expected = IllegalStateException.class)
	public void testOnJoinActionFromInvitedWithoutInvitationId()
			throws Exception {
		InviteeSession session =
				new InviteeSession(contactGroupId, privateGroupId,
						lastLocalMessageId, null, localTimestamp,
						inviteTimestamp, INVITED);
		engine.onJoinAction(txn, session);
	}

	// onLeaveAction

	@Test
	public void testOnLeaveActionFromStart() throws Exception {
		InviteeSession session = getDefaultSession(START);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromLeft() throws Exception {
		InviteeSession session = getDefaultSession(LEFT);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromDissolved() throws Exception {
		InviteeSession session = getDefaultSession(DISSOLVED);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromError() throws Exception {
		InviteeSession session = getDefaultSession(ERROR);
		assertEquals(session, engine.onLeaveAction(txn, session, false));
	}

	@Test
	public void testOnLeaveActionFromInvited() throws Exception {
		expectMarkMessageAvailableToAnswer(lastRemoteMessageId, false);
		expectSendLeaveMessage(true);
		context.checking(new Expectations() {{
			oneOf(conversationManager).trackOutgoingMessage(txn, message);
		}});

		InviteeSession session = getDefaultSession(INVITED);
		InviteeSession newSession = engine.onLeaveAction(txn, session, false);

		assertEquals(START, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test(expected = IllegalStateException.class)
	public void testOnLeaveActionFromInvitedWithoutInvitationId()
			throws Exception {
		InviteeSession session =
				new InviteeSession(contactGroupId, privateGroupId,
						lastLocalMessageId, null, localTimestamp,
						inviteTimestamp, INVITED);
		engine.onJoinAction(txn, session);
	}

	@Test
	public void testOnLeaveActionFromAccepted() throws Exception {
		expectSendLeaveMessage(false);
		expectSetPrivateGroupVisibility(INVISIBLE);
		InviteeSession session = getDefaultSession(ACCEPTED);
		InviteeSession newSession = engine.onLeaveAction(txn, session, false);

		assertEquals(LEFT, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnLeaveActionFromJoined() throws Exception {
		expectSendLeaveMessage(false);
		expectSetPrivateGroupVisibility(INVISIBLE);
		InviteeSession session = getDefaultSession(JOINED);
		InviteeSession newSession = engine.onLeaveAction(txn, session, false);

		assertEquals(LEFT, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(session, newSession);
	}

	// onMemberAddedAction

	@Test
	public void testOnMemberAddedFromStart() {
		InviteeSession session = getDefaultSession(START);
		assertEquals(session, engine.onMemberAddedAction(txn, session));
	}

	@Test
	public void testOnMemberAddedFromInvited() {
		InviteeSession session = getDefaultSession(INVITED);
		assertEquals(session, engine.onMemberAddedAction(txn, session));
	}

	@Test
	public void testOnMemberAddedFromAccepted() {
		InviteeSession session = getDefaultSession(ACCEPTED);
		assertEquals(session, engine.onMemberAddedAction(txn, session));
	}

	@Test
	public void testOnMemberAddedFromJoined() {
		InviteeSession session = getDefaultSession(JOINED);
		assertEquals(session, engine.onMemberAddedAction(txn, session));
	}

	@Test
	public void testOnMemberAddedFromLeft() {
		InviteeSession session = getDefaultSession(LEFT);
		assertEquals(session, engine.onMemberAddedAction(txn, session));
	}

	@Test
	public void testOnMemberAddedFromDissolved() {
		InviteeSession session = getDefaultSession(DISSOLVED);
		assertEquals(session, engine.onMemberAddedAction(txn, session));
	}

	@Test
	public void testOnMemberAddedFromError() {
		InviteeSession session = getDefaultSession(ERROR);
		assertEquals(session, engine.onMemberAddedAction(txn, session));
	}

	// onInviteMessage

	@Test
	public void testOnInviteMessageFromStartWithLowerTimestamp()
			throws Exception {
		InviteeSession session = getDefaultSession(START);
		assertTrue(
				inviteMessage.getTimestamp() <= session.getInviteTimestamp());

		expectAbortWhenSubscribedToGroup();
		InviteeSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromStartButNotCreator() throws Exception {
		InviteeSession session = getDefaultSession(START);
		InviteMessage properInviteMessage =
				new InviteMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, session.getInviteTimestamp() + 1,
						privateGroup.getName(), privateGroup.getCreator(),
						privateGroup.getSalt(),
						getRandomString(MAX_GROUP_INVITATION_TEXT_LENGTH),
						signature, NO_AUTO_DELETE_TIMER);
		Contact notCreatorContact = getContact(contactId, getAuthor(),
				localAuthor.getId(), true);

		expectGetContactId();
		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contactId);
			will(returnValue(notCreatorContact));
		}});
		expectAbortWhenSubscribedToGroup();

		InviteeSession newSession =
				engine.onInviteMessage(txn, session, properInviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromStart() throws Exception {
		InviteeSession session = getDefaultSession(START);
		InviteMessage properInviteMessage =
				new InviteMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, session.getInviteTimestamp() + 1,
						privateGroup.getName(), privateGroup.getCreator(),
						privateGroup.getSalt(), "msg", signature,
						NO_AUTO_DELETE_TIMER);
		assertEquals(contact.getAuthor(), privateGroup.getCreator());

		expectGetContactId();
		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
		}});
		expectMarkMessageVisibleInUi(properInviteMessage.getId());
		expectMarkMessageAvailableToAnswer(properInviteMessage.getId(), true);
		expectTrackUnreadMessage(properInviteMessage.getTimestamp());
		expectReceiveAutoDeleteTimer(properInviteMessage);
		context.checking(new Expectations() {{
			oneOf(privateGroupFactory)
					.createPrivateGroup(properInviteMessage.getGroupName(),
							properInviteMessage.getCreator(),
							properInviteMessage.getSalt());
			will(returnValue(privateGroup));
		}});

		InviteeSession newSession =
				engine.onInviteMessage(txn, session, properInviteMessage);

		assertEquals(INVITED, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(properInviteMessage.getId(),
				newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertEquals(properInviteMessage.getTimestamp(),
				newSession.getInviteTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromInvited() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(INVITED);
		InviteeSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromAccepted() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(ACCEPTED);
		InviteeSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromJoined() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(JOINED);
		InviteeSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromLeft() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(LEFT);
		InviteeSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromDissolved() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(DISSOLVED);
		InviteeSession newSession =
				engine.onInviteMessage(txn, session, inviteMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnInviteMessageFromError() throws Exception {
		InviteeSession session = getDefaultSession(ERROR);
		assertEquals(session,
				engine.onInviteMessage(txn, session, inviteMessage));
	}

	// onJoinMessage

	@Test
	public void testOnJoinMessageFromStart() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(START);
		InviteeSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromInvited() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(INVITED);
		InviteeSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromJoined() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(JOINED);
		InviteeSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromLeft() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(LEFT);
		InviteeSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromDissolved() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(DISSOLVED);
		InviteeSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromAcceptedWithWrongTimestamp()
			throws Exception {
		InviteeSession session = getDefaultSession(ACCEPTED);
		assertTrue(joinMessage.getTimestamp() <= session.getInviteTimestamp());

		expectAbortWhenSubscribedToGroup();
		InviteeSession newSession =
				engine.onJoinMessage(txn, session, joinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromAcceptedWithInvalidDependency()
			throws Exception {
		InviteeSession session = getDefaultSession(ACCEPTED);
		JoinMessage invalidJoinMessage =
				new JoinMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, session.getInviteTimestamp() + 1,
						lastLocalMessageId, NO_AUTO_DELETE_TIMER);
		assertFalse(invalidJoinMessage.getTimestamp() <=
				session.getInviteTimestamp());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotNull(invalidJoinMessage.getPreviousMessageId());
		assertNotEquals(session.getLastRemoteMessageId(),
				invalidJoinMessage.getPreviousMessageId());

		expectAbortWhenSubscribedToGroup();
		InviteeSession newSession =
				engine.onJoinMessage(txn, session, invalidJoinMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromAccepted() throws Exception {
		InviteeSession session = getDefaultSession(ACCEPTED);
		JoinMessage properJoinMessage =
				new JoinMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, session.getInviteTimestamp() + 1,
						lastRemoteMessageId, NO_AUTO_DELETE_TIMER);
		assertFalse(properJoinMessage.getTimestamp() <=
				session.getInviteTimestamp());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotNull(properJoinMessage.getPreviousMessageId());
		assertEquals(session.getLastRemoteMessageId(),
				properJoinMessage.getPreviousMessageId());

		expectSetPrivateGroupVisibility(SHARED);


		InviteeSession newSession =
				engine.onJoinMessage(txn, session, properJoinMessage);

		assertEquals(JOINED, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(properJoinMessage.getId(),
				newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertEquals(session.getInviteTimestamp(),
				newSession.getInviteTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnJoinMessageFromFromError() throws Exception {
		InviteeSession session = getDefaultSession(ERROR);
		assertEquals(session, engine.onJoinMessage(txn, session, joinMessage));
	}

	// onLeaveMessage

	@Test
	public void testOnLeaveMessageFromStart() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(START);
		InviteeSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromDissolved() throws Exception {
		expectAbortWhenSubscribedToGroup();
		InviteeSession session = getDefaultSession(DISSOLVED);
		InviteeSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromInvitedWithWrongTimestamp()
			throws Exception {
		InviteeSession session = getDefaultSession(INVITED);
		assertTrue(leaveMessage.getTimestamp() <= session.getInviteTimestamp());

		expectAbortWhenSubscribedToGroup();
		InviteeSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromLeftWithWrongTimestamp()
			throws Exception {
		InviteeSession session = getDefaultSession(LEFT);
		assertTrue(leaveMessage.getTimestamp() <= session.getInviteTimestamp());

		expectAbortWhenSubscribedToGroup();
		InviteeSession newSession =
				engine.onLeaveMessage(txn, session, leaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromInvitedWithInvalidDependency()
			throws Exception {
		InviteeSession session = getDefaultSession(INVITED);
		LeaveMessage invalidLeaveMessage =
				new LeaveMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, session.getInviteTimestamp() + 1, null,
						NO_AUTO_DELETE_TIMER);
		assertFalse(invalidLeaveMessage.getTimestamp() <=
				session.getInviteTimestamp());
		assertNull(invalidLeaveMessage.getPreviousMessageId());

		expectAbortWhenSubscribedToGroup();
		InviteeSession newSession =
				engine.onLeaveMessage(txn, session, invalidLeaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromLeftWithInvalidDependency()
			throws Exception {
		InviteeSession session = getDefaultSession(LEFT);
		LeaveMessage invalidLeaveMessage =
				new LeaveMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, session.getInviteTimestamp() + 1, null,
						NO_AUTO_DELETE_TIMER);
		assertFalse(invalidLeaveMessage.getTimestamp() <=
				session.getInviteTimestamp());
		assertNull(invalidLeaveMessage.getPreviousMessageId());

		expectAbortWhenSubscribedToGroup();
		InviteeSession newSession =
				engine.onLeaveMessage(txn, session, invalidLeaveMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromInvited() throws Exception {
		InviteeSession session = getDefaultSession(INVITED);
		LeaveMessage properLeaveMessage =
				new LeaveMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, session.getInviteTimestamp() + 1,
						lastRemoteMessageId, NO_AUTO_DELETE_TIMER);
		assertFalse(properLeaveMessage.getTimestamp() <=
				session.getInviteTimestamp());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotNull(properLeaveMessage.getPreviousMessageId());
		assertEquals(session.getLastRemoteMessageId(),
				properLeaveMessage.getPreviousMessageId());

		expectMarkInvitesUnavailableToAnswer();
		InviteeSession newSession =
				engine.onLeaveMessage(txn, session, properLeaveMessage);

		assertEquals(DISSOLVED, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(properLeaveMessage.getId(),
				newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertEquals(session.getInviteTimestamp(),
				newSession.getInviteTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	@Test
	public void testOnLeaveMessageFromLeft() throws Exception {
		InviteeSession session = getDefaultSession(LEFT);
		LeaveMessage properLeaveMessage =
				new LeaveMessage(new MessageId(getRandomId()), contactGroupId,
						privateGroupId, session.getInviteTimestamp() + 1,
						lastRemoteMessageId, NO_AUTO_DELETE_TIMER);
		assertFalse(properLeaveMessage.getTimestamp() <=
				session.getInviteTimestamp());
		assertNotNull(session.getLastRemoteMessageId());
		assertNotNull(properLeaveMessage.getPreviousMessageId());
		assertEquals(session.getLastRemoteMessageId(),
				properLeaveMessage.getPreviousMessageId());

		expectMarkInvitesUnavailableToAnswer();
		InviteeSession newSession =
				engine.onLeaveMessage(txn, session, properLeaveMessage);

		assertEquals(DISSOLVED, newSession.getState());
		assertEquals(session.getLastLocalMessageId(),
				newSession.getLastLocalMessageId());
		assertEquals(properLeaveMessage.getId(),
				newSession.getLastRemoteMessageId());
		assertEquals(session.getLocalTimestamp(),
				newSession.getLocalTimestamp());
		assertEquals(session.getInviteTimestamp(),
				newSession.getInviteTimestamp());
		assertSessionConstantsUnchanged(session, newSession);
	}

	// onAbortMessage

	@Test
	public void testOnAbortMessageWhenNotSubscribed() throws Exception {
		InviteeSession session = getDefaultSession(START);

		expectAbortWhenNotSubscribedToGroup();
		InviteeSession newSession =
				engine.onAbortMessage(txn, session, abortMessage);
		assertSessionAborted(session, newSession);
	}

	@Test
	public void testOnAbortMessageWhenSubscribed() throws Exception {
		InviteeSession session = getDefaultSession(START);

		expectAbortWhenSubscribedToGroup();
		InviteeSession newSession =
				engine.onAbortMessage(txn, session, abortMessage);
		assertSessionAborted(session, newSession);
	}

	// helper methods

	private void expectMarkMessageAvailableToAnswer(MessageId id,
			boolean available) throws Exception {
		BdfDictionary meta = new BdfDictionary();
		context.checking(new Expectations() {{
			oneOf(messageEncoder)
					.setAvailableToAnswer(meta, available);
			oneOf(clientHelper)
					.mergeMessageMetadata(txn, id, meta);
		}});
	}

	private void expectAbortWhenSubscribedToGroup() throws Exception {
		expectAbort(true);
	}

	private void expectAbortWhenNotSubscribedToGroup() throws Exception {
		expectAbort(false);
	}

	private void expectAbort(boolean subscribed) throws Exception {
		expectMarkInvitesUnavailableToAnswer();
		if (subscribed) {
			expectIsSubscribedPrivateGroup();
			expectSetPrivateGroupVisibility(INVISIBLE);
		} else {
			expectIsNotSubscribedPrivateGroup();
		}
		expectSendAbortMessage();
	}

	private void expectMarkInvitesUnavailableToAnswer() throws Exception {
		BdfDictionary query = BdfDictionary.of(new BdfEntry("query", ""));
		Collection<MessageId> invites = singletonList(lastRemoteMessageId);
		context.checking(new Expectations() {{
			oneOf(messageParser)
					.getInvitesAvailableToAnswerQuery(privateGroupId);
			will(returnValue(query));
			oneOf(clientHelper).getMessageIds(txn, contactGroupId, query);
			will(returnValue(invites));
		}});
		expectMarkMessageAvailableToAnswer(lastRemoteMessageId, false);
	}

	private void assertSessionAborted(InviteeSession oldSession,
			InviteeSession newSession) {
		assertEquals(ERROR, newSession.getState());
		assertSessionRecordedSentMessage(newSession);
		assertSessionConstantsUnchanged(oldSession, newSession);
	}

}
