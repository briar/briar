package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationItem;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;
import org.jmock.AbstractExpectations;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.fail;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.GROUP_SALT_LENGTH;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_NAME_LENGTH;
import static org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager.CLIENT_ID;
import static org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager.MAJOR_VERSION;
import static org.briarproject.briar.privategroup.invitation.MessageType.ABORT;
import static org.briarproject.briar.privategroup.invitation.MessageType.INVITE;
import static org.briarproject.briar.privategroup.invitation.MessageType.JOIN;
import static org.briarproject.briar.privategroup.invitation.MessageType.LEAVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupInvitationManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final ClientVersioningManager clientVersioningManager =
			context.mock(ClientVersioningManager.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);
	private final PrivateGroupFactory privateGroupFactory =
			context.mock(PrivateGroupFactory.class);
	private final PrivateGroupManager privateGroupManager =
			context.mock(PrivateGroupManager.class);
	private final MessageParser messageParser =
			context.mock(MessageParser.class);
	private final SessionParser sessionParser =
			context.mock(SessionParser.class);
	private final SessionEncoder sessionEncoder =
			context.mock(SessionEncoder.class);
	private final ProtocolEngineFactory engineFactory =
			context.mock(ProtocolEngineFactory.class);

	private final CreatorProtocolEngine creatorEngine;
	private final InviteeProtocolEngine inviteeEngine;
	private final PeerProtocolEngine peerEngine;
	private final CreatorSession creatorSession;
	private final InviteeSession inviteeSession;
	private final PeerSession peerSession;
	private final MessageMetadata messageMetadata;

	private final GroupInvitationManagerImpl groupInvitationManager;

	private final Transaction txn = new Transaction(null, false);
	private final Author author = getAuthor();
	private final Contact contact = getContact(author,
			new AuthorId(getRandomId()), true);
	private final ContactId contactId = contact.getId();
	private final Group localGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Group privateGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final BdfDictionary meta = BdfDictionary.of(new BdfEntry("m", "e"));
	private final Message message = getMessage(contactGroup.getId());
	private final BdfList body = BdfList.of("body");
	private final SessionId sessionId =
			new SessionId(privateGroup.getId().getBytes());
	private final Message storageMessage = getMessage(contactGroup.getId());
	private final BdfDictionary bdfSession =
			BdfDictionary.of(new BdfEntry("f", "o"));
	private final Map<MessageId, BdfDictionary> oneResult =
			Collections.singletonMap(storageMessage.getId(), bdfSession);
	private final Map<MessageId, BdfDictionary> noResults =
			Collections.emptyMap();


	public GroupInvitationManagerImplTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		creatorEngine = context.mock(CreatorProtocolEngine.class);
		inviteeEngine = context.mock(InviteeProtocolEngine.class);
		peerEngine = context.mock(PeerProtocolEngine.class);

		creatorSession = context.mock(CreatorSession.class);
		inviteeSession = context.mock(InviteeSession.class);
		peerSession = context.mock(PeerSession.class);

		messageMetadata = context.mock(MessageMetadata.class);

		context.checking(new Expectations() {{
			oneOf(engineFactory).createCreatorEngine();
			will(returnValue(creatorEngine));
			oneOf(engineFactory).createInviteeEngine();
			will(returnValue(inviteeEngine));
			oneOf(engineFactory).createPeerEngine();
			will(returnValue(peerEngine));
		}});
		MetadataParser metadataParser = context.mock(MetadataParser.class);
		MessageTracker messageTracker = context.mock(MessageTracker.class);
		groupInvitationManager = new GroupInvitationManagerImpl(db,
				clientHelper, clientVersioningManager, metadataParser,
				messageTracker, contactGroupFactory, privateGroupFactory,
				privateGroupManager, messageParser, sessionParser,
				sessionEncoder, engineFactory);
	}

	@Test
	public void testDatabaseOpenHookFirstTime() throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createLocalGroup(CLIENT_ID,
					MAJOR_VERSION);
			will(returnValue(localGroup));
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, localGroup);
			oneOf(db).getContacts(txn);
			will(returnValue(Collections.singletonList(contact)));
		}});
		expectAddingContact(contact);
		groupInvitationManager.onDatabaseOpened(txn);
	}

	@Test
	public void testOpenDatabaseHookSubsequentTime() throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createLocalGroup(CLIENT_ID,
					MAJOR_VERSION);
			will(returnValue(localGroup));
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(true));
		}});
		groupInvitationManager.onDatabaseOpened(txn);
	}

	private void expectAddingContact(Contact c) throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, c);
			will(returnValue(contactGroup));
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(clientVersioningManager).getClientVisibility(txn, contactId,
					CLIENT_ID, MAJOR_VERSION);
			will(returnValue(SHARED));
			oneOf(db).setGroupVisibility(txn, c.getId(), contactGroup.getId(),
					SHARED);
			oneOf(clientHelper)
					.setContactId(txn, contactGroup.getId(), contactId);
			oneOf(db).getGroups(txn, PrivateGroupManager.CLIENT_ID,
					PrivateGroupManager.MAJOR_VERSION);
			will(returnValue(Collections.singletonList(privateGroup)));
			oneOf(privateGroupManager).isMember(txn, privateGroup.getId(),
					c.getAuthor());
			will(returnValue(true));
		}});
		expectAddingMember(privateGroup.getId(), c);
	}

	private void expectAddingMember(GroupId g, Contact c) throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, c);
			will(returnValue(contactGroup));
		}});
		expectGetSession(noResults, new SessionId(g.getBytes()),
				contactGroup.getId());

		context.checking(new Expectations() {{
			oneOf(peerEngine).onMemberAddedAction(with(txn),
					with(any(PeerSession.class)));
			will(returnValue(peerSession));
		}});
		expectStoreSession(peerSession, storageMessage.getId());
		expectCreateStorageId();
	}

	private void expectCreateStorageId() throws DbException {
		context.checking(new Expectations() {{
			oneOf(clientHelper)
					.createMessageForStoringMetadata(contactGroup.getId());
			will(returnValue(storageMessage));
			oneOf(db).addLocalMessage(txn, storageMessage, new Metadata(),
					false, false);
		}});
	}

	private void expectStoreSession(Session<?> session, MessageId storageId)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(sessionEncoder).encodeSession(session);
			will(returnValue(meta));
			oneOf(clientHelper).mergeMessageMetadata(txn, storageId, meta);
		}});
	}

	private void expectGetSession(Map<MessageId, BdfDictionary> results,
			SessionId sessionId, GroupId contactGroupId) throws Exception {
		BdfDictionary query = BdfDictionary.of(new BdfEntry("q", "u"));
		context.checking(new Expectations() {{
			oneOf(sessionParser).getSessionQuery(sessionId);
			will(returnValue(query));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroupId, query);
			will(returnValue(results));
		}});
	}

	@Test
	public void testAddingContact() throws Exception {
		expectAddingContact(contact);
		groupInvitationManager.addingContact(txn, contact);
	}

	@Test
	public void testRemovingContact() throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).removeGroup(txn, contactGroup);
		}});
		groupInvitationManager.removingContact(txn, contact);
	}

	@Test(expected = FormatException.class)
	public void testIncomingUnknownMessage() throws Exception {
		expectFirstIncomingMessage(Role.INVITEE, ABORT);
		groupInvitationManager.incomingMessage(txn, message, body, meta);
	}

	@Test
	public void testIncomingFirstInviteMessage() throws Exception {
		expectFirstIncomingMessage(Role.INVITEE, INVITE);
		groupInvitationManager.incomingMessage(txn, message, body, meta);
	}

	@Test
	public void testIncomingFirstJoinMessage() throws Exception {
		expectFirstIncomingMessage(Role.PEER, JOIN);
		groupInvitationManager.incomingMessage(txn, message, body, meta);
	}

	@Test
	public void testIncomingInviteMessage() throws Exception {
		expectIncomingMessage(Role.INVITEE, INVITE);
		groupInvitationManager.incomingMessage(txn, message, body, meta);
	}

	@Test
	public void testIncomingJoinMessage() throws Exception {
		expectIncomingMessage(Role.INVITEE, JOIN);
		groupInvitationManager.incomingMessage(txn, message, body, meta);
	}

	@Test
	public void testIncomingJoinMessageForCreator() throws Exception {
		expectIncomingMessage(Role.CREATOR, JOIN);
		groupInvitationManager.incomingMessage(txn, message, body, meta);
	}

	@Test
	public void testIncomingLeaveMessage() throws Exception {
		expectIncomingMessage(Role.INVITEE, LEAVE);
		groupInvitationManager.incomingMessage(txn, message, body, meta);
	}

	@Test
	public void testIncomingAbortMessage() throws Exception {
		expectIncomingMessage(Role.INVITEE, ABORT);
		groupInvitationManager.incomingMessage(txn, message, body, meta);
	}

	private void expectFirstIncomingMessage(Role role, MessageType type)
			throws Exception {
		expectParseMessageMetadata();
		expectGetSession(noResults, sessionId, contactGroup.getId());
		Session<?> session =
				expectHandleFirstMessage(role, messageMetadata, type);
		if (session != null) {
			expectCreateStorageId();
			expectStoreSession(session, storageMessage.getId());
		}
	}

	private void expectParseMessageMetadata() throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageParser).parseMetadata(meta);
			will(returnValue(messageMetadata));
			oneOf(messageMetadata).getPrivateGroupId();
			will(returnValue(privateGroup.getId()));
		}});

	}

	private void expectIncomingMessage(Role role, MessageType type)
			throws Exception {
		BdfDictionary bdfSession = BdfDictionary.of(new BdfEntry("f", "o"));
		expectIncomingMessageWithSession(role, type, bdfSession);
	}

	private void expectIncomingMessageWithSession(Role role, MessageType type,
			BdfDictionary bdfSession) throws Exception {
		expectParseMessageMetadata();
		expectGetSession(oneResult, sessionId, contactGroup.getId());
		Session<?> session =
				expectHandleMessage(role, messageMetadata, bdfSession, type);
		expectStoreSession(session, storageMessage.getId());
	}

	@Nullable
	private Session<?> expectHandleFirstMessage(Role role,
			MessageMetadata messageMetadata, MessageType type)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageMetadata).getPrivateGroupId();
			will(returnValue(privateGroup.getId()));
			oneOf(messageMetadata).getMessageType();
			will(returnValue(type));
		}});
		if (type == ABORT || type == LEAVE) return null;

		AbstractProtocolEngine engine;
		Session session;
		if (type == INVITE) {
			assertEquals(Role.INVITEE, role);
			engine = inviteeEngine;
			session = inviteeSession;
		} else if (type == JOIN) {
			assertEquals(Role.PEER, role);
			engine = peerEngine;
			session = peerSession;
		} else {
			throw new AssertionError();
		}
		expectIndividualMessage(type, engine, session);
		return session;
	}

	@Nullable
	private Session<?> expectHandleMessage(Role role,
			MessageMetadata messageMetadata, BdfDictionary state,
			MessageType type) throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageMetadata).getMessageType();
			will(returnValue(type));
			oneOf(sessionParser).getRole(state);
			will(returnValue(role));
		}});
		if (role == Role.CREATOR) {
			context.checking(new Expectations() {{
				oneOf(sessionParser)
						.parseCreatorSession(contactGroup.getId(), state);
				will(returnValue(creatorSession));
			}});
			expectIndividualMessage(type, creatorEngine, creatorSession);
			return creatorSession;
		} else if (role == Role.INVITEE) {
			context.checking(new Expectations() {{
				oneOf(sessionParser)
						.parseInviteeSession(contactGroup.getId(), state);
				will(returnValue(inviteeSession));
			}});
			expectIndividualMessage(type, inviteeEngine, inviteeSession);
			return inviteeSession;
		} else if (role == Role.PEER) {
			context.checking(new Expectations() {{
				oneOf(sessionParser)
						.parsePeerSession(contactGroup.getId(), state);
				will(returnValue(peerSession));
			}});
			expectIndividualMessage(type, peerEngine, peerSession);
			return peerSession;
		} else {
			throw new AssertionError();
		}
	}

	private <S extends Session<?>> void expectIndividualMessage(
			MessageType type, ProtocolEngine<S> engine, S session)
			throws Exception {
		if (type == INVITE) {
			InviteMessage msg = context.mock(InviteMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseInviteMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onInviteMessage(with(txn),
						with(AbstractExpectations.<S>anything()), with(msg));
				will(returnValue(session));
			}});
		} else if (type == JOIN) {
			JoinMessage msg = context.mock(JoinMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseJoinMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onJoinMessage(with(txn),
						with(AbstractExpectations.<S>anything()), with(msg));
				will(returnValue(session));
			}});
		} else if (type == LEAVE) {
			LeaveMessage msg = context.mock(LeaveMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseLeaveMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onLeaveMessage(with(txn),
						with(AbstractExpectations.<S>anything()), with(msg));
				will(returnValue(session));
			}});
		} else if (type == ABORT) {
			AbortMessage msg = context.mock(AbortMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseAbortMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onAbortMessage(with(txn),
						with(AbstractExpectations.<S>anything()), with(msg));
				will(returnValue(session));
			}});
		} else {
			fail();
		}
	}

	@Test
	public void testSendFirstInvitation() throws Exception {
		String text = "Invitation text for first invitation";
		long time = 42L;
		byte[] signature = getRandomBytes(42);

		expectGetSession(noResults, sessionId, contactGroup.getId());
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
		}});
		expectCreateStorageId();
		context.checking(new Expectations() {{
			oneOf(creatorEngine).onInviteAction(with(txn),
					with(any(CreatorSession.class)), with(text), with(time),
					with(signature));
			will(returnValue(creatorSession));
		}});
		expectStoreSession(creatorSession, storageMessage.getId());
		context.checking(new Expectations() {{
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		groupInvitationManager.sendInvitation(privateGroup.getId(), contactId,
				text, time, signature);
	}

	@Test
	public void testSendSubsequentInvitation() throws Exception {
		String text = "Invitation text for subsequent invitation";
		long time = 43L;
		byte[] signature = getRandomBytes(43);

		expectGetSession(oneResult, sessionId, contactGroup.getId());
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(sessionParser)
					.parseCreatorSession(contactGroup.getId(), bdfSession);
			will(returnValue(creatorSession));
			oneOf(creatorEngine).onInviteAction(with(txn),
					with(any(CreatorSession.class)), with(text), with(time),
					with(signature));
			will(returnValue(creatorSession));
		}});
		expectStoreSession(creatorSession, storageMessage.getId());
		context.checking(new Expectations() {{
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		groupInvitationManager.sendInvitation(privateGroup.getId(), contactId,
				text, time, signature);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRespondToInvitationWithoutSession() throws Exception {
		SessionId sessionId = new SessionId(getRandomId());

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).endTransaction(txn);
		}});
		expectGetSession(noResults, sessionId, contactGroup.getId());

		groupInvitationManager.respondToInvitation(contactId, sessionId, true);
	}

	@Test
	public void testAcceptInvitationWithSession() throws Exception {
		expectRespondToInvitation(sessionId, true);
		groupInvitationManager
				.respondToInvitation(contactId, sessionId, true);
	}

	@Test
	public void testDeclineInvitationWithSession() throws Exception {
		expectRespondToInvitation(sessionId, false);
		groupInvitationManager
				.respondToInvitation(contactId, sessionId, false);
	}

	@Test
	public void testAcceptInvitationWithGroupId() throws Exception {
		PrivateGroup pg = new PrivateGroup(privateGroup,
				getRandomString(MAX_GROUP_NAME_LENGTH), author,
				getRandomBytes(GROUP_SALT_LENGTH));

		expectRespondToInvitation(sessionId, true);
		groupInvitationManager.respondToInvitation(contactId, pg, true);
	}

	@Test
	public void testDeclineInvitationWithGroupId() throws Exception {
		PrivateGroup pg = new PrivateGroup(privateGroup,
				getRandomString(MAX_GROUP_NAME_LENGTH), author,
				getRandomBytes(GROUP_SALT_LENGTH));

		expectRespondToInvitation(sessionId, false);
		groupInvitationManager.respondToInvitation(contactId, pg, false);
	}

	private void expectRespondToInvitation(SessionId sessionId, boolean accept)
			throws Exception {
		expectGetSession(oneResult, sessionId, contactGroup.getId());
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(sessionParser)
					.parseInviteeSession(contactGroup.getId(), bdfSession);
			will(returnValue(inviteeSession));
			if (accept) oneOf(inviteeEngine).onJoinAction(txn, inviteeSession);
			else oneOf(inviteeEngine).onLeaveAction(txn, inviteeSession);
			will(returnValue(inviteeSession));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		expectStoreSession(inviteeSession, storageMessage.getId());
	}

	@Test
	public void testRevealRelationship() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(sessionParser)
					.parsePeerSession(contactGroup.getId(), bdfSession);
			will(returnValue(peerSession));
			oneOf(peerEngine).onJoinAction(txn, peerSession);
			will(returnValue(peerSession));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		expectGetSession(oneResult, sessionId, contactGroup.getId());
		expectStoreSession(peerSession, storageMessage.getId());

		groupInvitationManager
				.revealRelationship(contactId, privateGroup.getId());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRevealRelationshipWithoutSession() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).endTransaction(txn);
		}});
		expectGetSession(noResults, sessionId, contactGroup.getId());

		groupInvitationManager
				.revealRelationship(contactId, privateGroup.getId());
	}

	@Test
	public void testGetInvitationMessages() throws Exception {
		BdfDictionary query = BdfDictionary.of(new BdfEntry("q", "u"));
		MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		BdfDictionary meta2 = BdfDictionary.of(new BdfEntry("m2", "e"));
		Map<MessageId, BdfDictionary> results = new HashMap<>();
		results.put(message.getId(), meta);
		results.put(messageId2, meta2);
		long time1 = 1L, time2 = 2L;
		MessageMetadata messageMetadata1 =
				new MessageMetadata(INVITE, privateGroup.getId(), time1, true,
						true, true, false, true, NO_AUTO_DELETE_TIMER);
		MessageMetadata messageMetadata2 =
				new MessageMetadata(JOIN, privateGroup.getId(), time2, true,
						true, true, true, false, NO_AUTO_DELETE_TIMER);
		InviteMessage invite =
				new InviteMessage(message.getId(), contactGroup.getId(),
						privateGroup.getId(), time1, "name", author,
						new byte[0], null, new byte[0], NO_AUTO_DELETE_TIMER);
		PrivateGroup pg =
				new PrivateGroup(privateGroup, invite.getGroupName(),
						invite.getCreator(), invite.getSalt());

		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(messageParser).getMessagesVisibleInUiQuery();
			will(returnValue(query));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId(), query);
			will(returnValue(results));
			// first message
			oneOf(messageParser).parseMetadata(meta);
			will(returnValue(messageMetadata1));
			oneOf(db).getMessageStatus(txn, contactId, message.getId());
			oneOf(messageParser).getInviteMessage(txn, message.getId());
			will(returnValue(invite));
			oneOf(privateGroupFactory).createPrivateGroup(invite.getGroupName(),
					invite.getCreator(), invite.getSalt());
			will(returnValue(pg));
			oneOf(db).containsGroup(txn, privateGroup.getId());
			will(returnValue(true));
			// second message
			oneOf(messageParser).parseMetadata(meta2);
			will(returnValue(messageMetadata2));
			oneOf(db).getMessageStatus(txn, contactId, messageId2);
		}});

		Collection<ConversationMessageHeader> messages =
				groupInvitationManager.getMessageHeaders(txn, contactId);
		assertEquals(2, messages.size());
		for (ConversationMessageHeader m : messages) {
			assertEquals(contactGroup.getId(), m.getGroupId());
			if (m.getId().equals(message.getId())) {
				assertTrue(m instanceof GroupInvitationRequest);
				assertEquals(time1, m.getTimestamp());
				assertEquals(pg, ((GroupInvitationRequest) m).getNameable());
			} else if (m.getId().equals(messageId2)) {
				assertTrue(m instanceof GroupInvitationResponse);
				assertEquals(time2, m.getTimestamp());
				assertEquals(pg.getId(),
						((GroupInvitationResponse) m).getShareableId());
			} else {
				throw new AssertionError();
			}
		}
	}

	@Test
	public void testGetInvitations() throws Exception {
		BdfDictionary query = BdfDictionary.of(new BdfEntry("q", "u"));
		Message message2 = getMessage(contactGroup.getId());
		BdfDictionary meta2 = BdfDictionary.of(new BdfEntry("m2", "e"));
		Map<MessageId, BdfDictionary> results = new HashMap<>();
		results.put(message.getId(), meta);
		results.put(message2.getId(), meta2);
		long time1 = 1L, time2 = 2L;
		String groupName = getRandomString(MAX_GROUP_NAME_LENGTH);
		byte[] salt = getRandomBytes(GROUP_SALT_LENGTH);
		InviteMessage inviteMessage1 =
				new InviteMessage(message.getId(), contactGroup.getId(),
						privateGroup.getId(), time1, groupName, author, salt,
						null, getRandomBytes(5), NO_AUTO_DELETE_TIMER);
		InviteMessage inviteMessage2 =
				new InviteMessage(message2.getId(), contactGroup.getId(),
						privateGroup.getId(), time2, groupName, author, salt,
						null, getRandomBytes(5), NO_AUTO_DELETE_TIMER);
		PrivateGroup pg = new PrivateGroup(privateGroup, groupName,
				author, salt);

		context.checking(new Expectations() {{
			oneOf(messageParser).getInvitesAvailableToAnswerQuery();
			will(returnValue(query));
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContacts(txn);
			will(returnValue(Collections.singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId(), query);
			will(returnValue(results));
			// message 1
			oneOf(messageParser).getInviteMessage(txn, message.getId());
			will(returnValue(inviteMessage1));
			oneOf(privateGroupFactory).createPrivateGroup(groupName, author,
					salt);
			will(returnValue(pg));
			// message 2
			oneOf(messageParser).getInviteMessage(txn, message2.getId());
			will(returnValue(inviteMessage2));
			oneOf(privateGroupFactory).createPrivateGroup(groupName, author,
					salt);
			will(returnValue(pg));
			// end transaction
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		Collection<GroupInvitationItem> items =
				groupInvitationManager.getInvitations();
		assertEquals(2, items.size());
		for (GroupInvitationItem i : items) {
			assertEquals(contact, i.getCreator());
			assertEquals(author, i.getCreator().getAuthor());
			assertEquals(privateGroup.getId(), i.getId());
			assertEquals(groupName, i.getName());
		}
	}

	@Test
	public void testIsInvitationAllowed() throws Exception {
		expectIsInvitationAllowed(CreatorState.START);
		assertTrue(groupInvitationManager
				.isInvitationAllowed(contact, privateGroup.getId()));
	}

	@Test
	public void testIsNotInvitationAllowed() throws Exception {
		expectIsInvitationAllowed(CreatorState.DISSOLVED);
		assertFalse(groupInvitationManager
				.isInvitationAllowed(contact, privateGroup.getId()));

		expectIsInvitationAllowed(CreatorState.ERROR);
		assertFalse(groupInvitationManager
				.isInvitationAllowed(contact, privateGroup.getId()));

		expectIsInvitationAllowed(CreatorState.INVITED);
		assertFalse(groupInvitationManager
				.isInvitationAllowed(contact, privateGroup.getId()));

		expectIsInvitationAllowed(CreatorState.JOINED);
		assertFalse(groupInvitationManager
				.isInvitationAllowed(contact, privateGroup.getId()));

		expectIsInvitationAllowed(CreatorState.LEFT);
		assertFalse(groupInvitationManager
				.isInvitationAllowed(contact, privateGroup.getId()));
	}

	private void expectIsInvitationAllowed(CreatorState state)
			throws Exception {
		expectGetSession(oneResult, sessionId, contactGroup.getId());
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(clientVersioningManager).getClientVisibility(txn, contactId,
					PrivateGroupManager.CLIENT_ID,
					PrivateGroupManager.MAJOR_VERSION);
			will(returnValue(SHARED));
			oneOf(sessionParser)
					.parseCreatorSession(contactGroup.getId(), bdfSession);
			will(returnValue(creatorSession));
			oneOf(creatorSession).getState();
			will(returnValue(state));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
	}

	@Test
	public void testAddingMember() throws Exception {
		expectAddingMember(privateGroup.getId(), contact);
		context.checking(new Expectations() {{
			oneOf(db).getContactsByAuthorId(txn, author.getId());
			will(returnValue(Collections.singletonList(contact)));
		}});
		groupInvitationManager.addingMember(txn, privateGroup.getId(), author);
	}

	@Test
	public void testRemovingGroupEndsSessions() throws Exception {
		Contact contact2 = getContact();
		Contact contact3 = getContact();
		Collection<Contact> contacts = asList(contact, contact2, contact3);

		Group contactGroup2 = getGroup(CLIENT_ID, MAJOR_VERSION);
		Group contactGroup3 = getGroup(CLIENT_ID, MAJOR_VERSION);

		MessageId storageId2 = new MessageId(getRandomId());
		MessageId storageId3 = new MessageId(getRandomId());
		BdfDictionary bdfSession2 =
				BdfDictionary.of(new BdfEntry("f2", "o"));
		BdfDictionary bdfSession3 =
				BdfDictionary.of(new BdfEntry("f3", "o"));

		expectGetSession(oneResult, sessionId, contactGroup.getId());
		expectGetSession(Collections.singletonMap(storageId2, bdfSession2),
				sessionId, contactGroup2.getId());
		expectGetSession(Collections.singletonMap(storageId3, bdfSession3),
				sessionId, contactGroup3.getId());

		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact2);
			will(returnValue(contactGroup2));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact3);
			will(returnValue(contactGroup3));
			// session 1
			oneOf(sessionParser).getRole(bdfSession);
			will(returnValue(Role.CREATOR));
			oneOf(sessionParser)
					.parseCreatorSession(contactGroup.getId(), bdfSession);
			will(returnValue(creatorSession));
			oneOf(creatorEngine).onLeaveAction(txn, creatorSession);
			will(returnValue(creatorSession));
			// session 2
			oneOf(sessionParser).getRole(bdfSession2);
			will(returnValue(Role.INVITEE));
			oneOf(sessionParser)
					.parseInviteeSession(contactGroup2.getId(), bdfSession2);
			will(returnValue(inviteeSession));
			oneOf(inviteeEngine).onLeaveAction(txn, inviteeSession);
			will(returnValue(inviteeSession));
			// session 3
			oneOf(sessionParser).getRole(bdfSession3);
			will(returnValue(Role.PEER));
			oneOf(sessionParser)
					.parsePeerSession(contactGroup3.getId(), bdfSession3);
			will(returnValue(peerSession));
			oneOf(peerEngine).onLeaveAction(txn, peerSession);
			will(returnValue(peerSession));
		}});

		expectStoreSession(creatorSession, storageMessage.getId());
		expectStoreSession(inviteeSession, storageId2);
		expectStoreSession(peerSession, storageId3);

		groupInvitationManager.removingGroup(txn, privateGroup.getId());
	}

}
