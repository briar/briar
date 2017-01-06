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
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationItem;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;
import org.briarproject.briar.api.sharing.InvitationMessage;
import org.jmock.AbstractExpectations;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static junit.framework.TestCase.fail;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getRandomString;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.GROUP_SALT_LENGTH;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_NAME_LENGTH;
import static org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager.CLIENT_ID;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.GROUP_KEY_CONTACT_ID;
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
	private final ContactId contactId = new ContactId(0);
	private final Author author =
			new Author(new AuthorId(getRandomId()), getRandomString(5),
					getRandomBytes(5));
	private final Contact contact =
			new Contact(contactId, author, new AuthorId(getRandomId()), true,
					true);
	private final Group contactGroup =
			new Group(new GroupId(getRandomId()), CLIENT_ID, getRandomBytes(5));
	private final Group privateGroup =
			new Group(new GroupId(getRandomId()), CLIENT_ID, getRandomBytes(5));
	private final BdfDictionary meta = BdfDictionary.of(new BdfEntry("m", "e"));
	private final Message message =
			new Message(new MessageId(getRandomId()), contactGroup.getId(),
					0L, getRandomBytes(MESSAGE_HEADER_LENGTH + 1));
	private final BdfList body = BdfList.of("body");
	private final SessionId sessionId =
			new SessionId(privateGroup.getId().getBytes());
	private final Message storageMessage =
			new Message(new MessageId(getRandomId()), contactGroup.getId(),
					0L, getRandomBytes(MESSAGE_HEADER_LENGTH + 1));
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
		groupInvitationManager =
				new GroupInvitationManagerImpl(db, clientHelper, metadataParser,
						messageTracker, contactGroupFactory,
						privateGroupFactory, privateGroupManager, messageParser,
						sessionParser, sessionEncoder, engineFactory);
	}

	@Test
	public void testCreateLocalState() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(Collections.singletonList(contact)));
		}});
		expectAddingContact(contact, true);
		groupInvitationManager.createLocalState(txn);
	}

	private void expectAddingContact(final Contact c,
			final boolean contactExists) throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, c);
			will(returnValue(contactGroup));
			oneOf(db).containsGroup(txn, contactGroup.getId());
			will(returnValue(contactExists));
		}});
		if (contactExists) return;

		final BdfDictionary meta = BdfDictionary
				.of(new BdfEntry(GROUP_KEY_CONTACT_ID, c.getId().getInt()));
		context.checking(new Expectations() {{
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(db).setGroupVisibility(txn, c.getId(), contactGroup.getId(),
					SHARED);
			oneOf(clientHelper)
					.mergeGroupMetadata(txn, contactGroup.getId(), meta);
			oneOf(db).getGroups(txn, PrivateGroupManager.CLIENT_ID);
			will(returnValue(Collections.singletonList(privateGroup)));
			oneOf(privateGroupManager)
					.isMember(txn, privateGroup.getId(), c.getAuthor());
			will(returnValue(true));
		}});
		expectAddingMember(privateGroup.getId(), c);
	}

	private void expectAddingMember(final GroupId g, final Contact c)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, c);
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
					false);
		}});
	}

	private void expectStoreSession(final Session session,
			final MessageId storageId) throws Exception {
		context.checking(new Expectations() {{
			oneOf(sessionEncoder).encodeSession(session);
			will(returnValue(meta));
			oneOf(clientHelper).mergeMessageMetadata(txn, storageId, meta);
		}});
	}

	private void expectGetSession(final Map<MessageId, BdfDictionary> results,
			final SessionId sessionId, final GroupId contactGroupId)
			throws Exception {
		final BdfDictionary query = BdfDictionary.of(new BdfEntry("q", "u"));
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
		expectAddingContact(contact, false);
		groupInvitationManager.addingContact(txn, contact);
	}

	@Test
	public void testRemovingContact() throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
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
		Session session = expectHandleFirstMessage(role, messageMetadata, type);
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

	private void expectIncomingMessageWithSession(final Role role,
			final MessageType type, final BdfDictionary bdfSession)
			throws Exception {
		expectParseMessageMetadata();
		expectGetSession(oneResult, sessionId, contactGroup.getId());
		Session session = expectHandleMessage(role, messageMetadata, bdfSession,
				type);
		expectStoreSession(session, storageMessage.getId());
	}

	@Nullable
	private Session expectHandleFirstMessage(Role role,
			final MessageMetadata messageMetadata, final MessageType type)
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
	private Session expectHandleMessage(final Role role,
			final MessageMetadata messageMetadata, final BdfDictionary state,
			final MessageType type) throws Exception {
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

	private <S extends Session> void expectIndividualMessage(
			final MessageType type, final ProtocolEngine<S> engine,
			final S session) throws Exception {
		if (type == INVITE) {
			final InviteMessage msg = context.mock(InviteMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseInviteMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onInviteMessage(with(txn),
						with(AbstractExpectations.<S>anything()), with(msg));
				will(returnValue(session));
			}});
		} else if (type == JOIN) {
			final JoinMessage msg = context.mock(JoinMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseJoinMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onJoinMessage(with(txn),
						with(AbstractExpectations.<S>anything()), with(msg));
				will(returnValue(session));
			}});
		} else if (type == LEAVE) {
			final LeaveMessage msg = context.mock(LeaveMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseLeaveMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onLeaveMessage(with(txn),
						with(AbstractExpectations.<S>anything()), with(msg));
				will(returnValue(session));
			}});
		} else if (type == ABORT) {
			final AbortMessage msg = context.mock(AbortMessage.class);
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
		final String msg = "Invitation text for first invitation";
		final long time = 42L;
		final byte[] signature = getRandomBytes(42);

		expectGetSession(noResults, sessionId, contactGroup.getId());
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
		}});
		expectCreateStorageId();
		context.checking(new Expectations() {{
			oneOf(creatorEngine).onInviteAction(with(txn),
					with(any(CreatorSession.class)), with(msg), with(time),
					with(signature));
			will(returnValue(creatorSession));
		}});
		expectStoreSession(creatorSession, storageMessage.getId());
		context.checking(new Expectations() {{
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		groupInvitationManager.sendInvitation(privateGroup.getId(), contactId,
				msg, time, signature);
	}

	@Test
	public void testSendSubsequentInvitation() throws Exception {
		final String msg = "Invitation text for subsequent invitation";
		final long time = 43L;
		final byte[] signature = getRandomBytes(43);

		expectGetSession(oneResult, sessionId, contactGroup.getId());
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(sessionParser)
					.parseCreatorSession(contactGroup.getId(), bdfSession);
			will(returnValue(creatorSession));
			oneOf(creatorEngine).onInviteAction(with(txn),
					with(any(CreatorSession.class)), with(msg), with(time),
					with(signature));
			will(returnValue(creatorSession));
		}});
		expectStoreSession(creatorSession, storageMessage.getId());
		context.checking(new Expectations() {{
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		groupInvitationManager.sendInvitation(privateGroup.getId(), contactId,
				msg, time, signature);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRespondToInvitationWithoutSession() throws Exception {
		final SessionId sessionId = new SessionId(getRandomId());

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
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

	private void expectRespondToInvitation(final SessionId sessionId,
			final boolean accept) throws Exception {
		expectGetSession(oneResult, sessionId, contactGroup.getId());
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
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
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
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
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(db).endTransaction(txn);
		}});
		expectGetSession(noResults, sessionId, contactGroup.getId());

		groupInvitationManager
				.revealRelationship(contactId, privateGroup.getId());
	}

	@Test
	public void testGetInvitationMessages() throws Exception {
		final BdfDictionary query = BdfDictionary.of(new BdfEntry("q", "u"));
		final MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		final BdfDictionary meta2 = BdfDictionary.of(new BdfEntry("m2", "e"));
		final Map<MessageId, BdfDictionary> results =
				new HashMap<MessageId, BdfDictionary>();
		results.put(message.getId(), meta);
		results.put(messageId2, meta2);
		final long time1 = 1L, time2 = 2L;
		final MessageMetadata messageMetadata1 =
				new MessageMetadata(INVITE, privateGroup.getId(), time1, true,
						true, true, false, true);
		final MessageMetadata messageMetadata2 =
				new MessageMetadata(JOIN, privateGroup.getId(), time2, true,
						true, true, true, false);
		final InviteMessage invite =
				new InviteMessage(message.getId(), contactGroup.getId(),
						privateGroup.getId(), time1, "name", author,
						new byte[0], null, new byte[0]);
		final PrivateGroup pg =
				new PrivateGroup(privateGroup, invite.getGroupName(),
						invite.getCreator(), invite.getSalt());

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
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
			// end transaction
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		Collection<InvitationMessage> messages =
				groupInvitationManager.getInvitationMessages(contactId);
		assertEquals(2, messages.size());
		for (InvitationMessage m : messages) {
			assertEquals(contactGroup.getId(), m.getGroupId());
			assertEquals(contactId, m.getContactId());
			if (m.getId().equals(message.getId())) {
				assertTrue(m instanceof GroupInvitationRequest);
				assertEquals(time1, m.getTimestamp());
			} else if (m.getId().equals(messageId2)) {
				assertTrue(m instanceof GroupInvitationResponse);
				assertEquals(time2, m.getTimestamp());
			} else {
				throw new AssertionError();
			}
		}
	}

	@Test
	public void testGetInvitations() throws Exception {
		final BdfDictionary query = BdfDictionary.of(new BdfEntry("q", "u"));
		final MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		final BdfDictionary meta2 = BdfDictionary.of(new BdfEntry("m2", "e"));
		final Map<MessageId, BdfDictionary> results =
				new HashMap<MessageId, BdfDictionary>();
		results.put(message.getId(), meta);
		results.put(messageId2, meta2);
		final Message message2 = new Message(messageId2, contactGroup.getId(),
				0L, getRandomBytes(MESSAGE_HEADER_LENGTH + 1));
		long time1 = 1L, time2 = 2L;
		final String groupName = getRandomString(MAX_GROUP_NAME_LENGTH);
		final byte[] salt = getRandomBytes(GROUP_SALT_LENGTH);
		final InviteMessage inviteMessage1 =
				new InviteMessage(message.getId(), contactGroup.getId(),
						privateGroup.getId(), time1, groupName, author, salt,
						null, getRandomBytes(5));
		final InviteMessage inviteMessage2 =
				new InviteMessage(message.getId(), contactGroup.getId(),
						privateGroup.getId(), time2, groupName, author, salt,
						null, getRandomBytes(5));
		final PrivateGroup pg = new PrivateGroup(privateGroup, groupName,
				author, salt);
		final BdfList body2 = BdfList.of("body2");

		context.checking(new Expectations() {{
			oneOf(messageParser).getInvitesAvailableToAnswerQuery();
			will(returnValue(query));
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContacts(txn);
			will(returnValue(Collections.singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
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
			oneOf(messageParser).getInviteMessage(txn, messageId2);
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

	private void expectIsInvitationAllowed(final CreatorState state)
			throws Exception {
		expectGetSession(oneResult, sessionId, contactGroup.getId());
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
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
		final Contact contact2 = new Contact(new ContactId(2), author,
				author.getId(), true, true);
		final Contact contact3 = new Contact(new ContactId(3), author,
				author.getId(), true, true);
		final Collection<Contact> contacts =
				Arrays.asList(contact, contact2, contact3);

		final Group contactGroup2 = new Group(new GroupId(getRandomId()),
				CLIENT_ID, getRandomBytes(5));
		final Group contactGroup3 = new Group(new GroupId(getRandomId()),
				CLIENT_ID, getRandomBytes(5));

		final MessageId storageId2 = new MessageId(getRandomId());
		final MessageId storageId3 = new MessageId(getRandomId());
		final BdfDictionary bdfSession2 =
				BdfDictionary.of(new BdfEntry("f2", "o"));
		final BdfDictionary bdfSession3 =
				BdfDictionary.of(new BdfEntry("f3", "o"));

		expectGetSession(oneResult, sessionId, contactGroup.getId());
		expectGetSession(Collections.singletonMap(storageId2, bdfSession2),
				sessionId, contactGroup2.getId());
		expectGetSession(Collections.singletonMap(storageId3, bdfSession3),
				sessionId, contactGroup3.getId());

		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact2);
			will(returnValue(contactGroup2));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact3);
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
