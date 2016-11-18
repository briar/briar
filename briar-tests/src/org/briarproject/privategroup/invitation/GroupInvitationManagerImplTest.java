package org.briarproject.privategroup.invitation;

import org.briarproject.BriarMockTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.privategroup.invitation.GroupInvitationItem;
import org.briarproject.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.api.privategroup.invitation.GroupInvitationResponse;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;
import org.jmock.AbstractExpectations;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.briarproject.TestUtils.getRandomBytes;
import static org.briarproject.TestUtils.getRandomId;
import static org.briarproject.TestUtils.getRandomString;
import static org.briarproject.api.privategroup.invitation.GroupInvitationManager.CLIENT_ID;
import static org.briarproject.api.sync.Group.Visibility.SHARED;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.privategroup.invitation.GroupInvitationConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.privategroup.invitation.MessageType.ABORT;
import static org.briarproject.privategroup.invitation.MessageType.INVITE;
import static org.briarproject.privategroup.invitation.MessageType.JOIN;
import static org.briarproject.privategroup.invitation.MessageType.LEAVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupInvitationManagerImplTest extends BriarMockTestCase {

	private final GroupInvitationManagerImpl groupInvitationManager;
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
	private final Group localGroup =
			new Group(new GroupId(getRandomId()), CLIENT_ID, getRandomBytes(5));
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
	private final BdfDictionary meta = BdfDictionary.of(new BdfEntry("f", "o"));
	private final Message message =
			new Message(new MessageId(getRandomId()), contactGroup.getId(),
					0L, getRandomBytes(MESSAGE_HEADER_LENGTH + 1));
	private final BdfList body = new BdfList();
	private final Message storageMessage =
			new Message(new MessageId(getRandomId()), contactGroup.getId(),
					0L, getRandomBytes(MESSAGE_HEADER_LENGTH + 1));

	public GroupInvitationManagerImplTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		creatorEngine = context.mock(CreatorProtocolEngine.class);
		inviteeEngine = context.mock(InviteeProtocolEngine.class);
		peerEngine = context.mock(PeerProtocolEngine.class);

		creatorSession = context.mock(CreatorSession.class);
		inviteeSession = context.mock(InviteeSession.class);
		peerSession = context.mock(PeerSession.class);

		context.checking(new Expectations() {{
			oneOf(engineFactory).createCreatorEngine();
			will(returnValue(creatorEngine));
			oneOf(engineFactory).createInviteeEngine();
			will(returnValue(inviteeEngine));
			oneOf(engineFactory).createPeerEngine();
			will(returnValue(peerEngine));
			oneOf(contactGroupFactory).createLocalGroup(CLIENT_ID);
			will(returnValue(localGroup));
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
			oneOf(db).addGroup(txn, localGroup);
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
		expectGetSession(Collections.<MessageId, BdfDictionary>emptyMap(),
				new SessionId(g.getBytes()));

		context.checking(new Expectations() {{
			oneOf(peerEngine).onMemberAddedAction(with(equal(txn)),
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

	private void expectGetSession(final Map<MessageId, BdfDictionary> result,
			final SessionId sessionId) throws Exception {
		final BdfDictionary query = BdfDictionary.of(new BdfEntry("q", "u"));
		context.checking(new Expectations() {{
			oneOf(sessionParser).getSessionQuery(sessionId);
			will(returnValue(query));
			oneOf(clientHelper)
					.getMessageMetadataAsDictionary(txn, contactGroup.getId(),
							query);
			will(returnValue(result));
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
		expectIncomingMessage(role, type,
				Collections.<MessageId, BdfDictionary>emptyMap());
	}

	private void expectIncomingMessage(Role role, MessageType type)
			throws Exception {
		BdfDictionary state = BdfDictionary.of(new BdfEntry("state", "test"));
		Map<MessageId, BdfDictionary> states =
				Collections.singletonMap(storageMessage.getId(), state);
		expectIncomingMessage(role, type, states);
	}

	private void expectIncomingMessage(final Role role,
			final MessageType type, final Map<MessageId, BdfDictionary> states)
			throws Exception {
		final MessageMetadata messageMetadata =
				context.mock(MessageMetadata.class);
		context.checking(new Expectations() {{
			oneOf(messageParser).parseMetadata(meta);
			will(returnValue(messageMetadata));
			oneOf(messageMetadata).getPrivateGroupId();
			will(returnValue(privateGroup.getId()));
		}});
		expectGetSession(states,
				new SessionId(privateGroup.getId().getBytes()));

		Session session;
		if (states.isEmpty()) {
			session = expectHandleFirstMessage(role, messageMetadata, type);
			if (session != null) {
				expectCreateStorageId();
				expectStoreSession(session, storageMessage.getId());
			}
		} else {
			assertEquals(1, states.size());
			session = expectHandleMessage(role, messageMetadata,
					states.values().iterator().next(), type);
			expectStoreSession(session, storageMessage.getId());
		}
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
			throw new RuntimeException();
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
			fail();
			throw new RuntimeException();
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
				oneOf(engine).onInviteMessage(with(equal(txn)),
						with(AbstractExpectations.<S>anything()),
						with(equal(msg)));
				will(returnValue(session));
			}});
		} else if (type == JOIN) {
			final JoinMessage msg = context.mock(JoinMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseJoinMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onJoinMessage(with(equal(txn)),
						with(AbstractExpectations.<S>anything()),
						with(equal(msg)));
				will(returnValue(session));
			}});
		} else if (type == LEAVE) {
			final LeaveMessage msg = context.mock(LeaveMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseLeaveMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onLeaveMessage(with(equal(txn)),
						with(AbstractExpectations.<S>anything()),
						with(equal(msg)));
				will(returnValue(session));
			}});
		} else if (type == ABORT) {
			final AbortMessage msg = context.mock(AbortMessage.class);
			context.checking(new Expectations() {{
				oneOf(messageParser).parseAbortMessage(message, body);
				will(returnValue(msg));
				oneOf(engine).onAbortMessage(with(equal(txn)),
						with(AbstractExpectations.<S>anything()),
						with(equal(msg)));
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
		final byte[] signature = TestUtils.getRandomBytes(42);
		Map<MessageId, BdfDictionary> states = Collections.emptyMap();

		expectGetSession(states,
				new SessionId(privateGroup.getId().getBytes()));
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
			oneOf(creatorEngine).onInviteAction(with(same(txn)),
					with(any(CreatorSession.class)), with(equal(msg)),
					with(equal(time)), with(equal(signature)));
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
		final byte[] signature = TestUtils.getRandomBytes(43);
		final BdfDictionary state =
				BdfDictionary.of(new BdfEntry("state", "test"));
		Map<MessageId, BdfDictionary> states =
				Collections.singletonMap(storageMessage.getId(), state);

		expectGetSession(states,
				new SessionId(privateGroup.getId().getBytes()));
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(sessionParser)
					.parseCreatorSession(contactGroup.getId(), state);
			will(returnValue(creatorSession));
			oneOf(creatorEngine).onInviteAction(with(same(txn)),
					with(any(CreatorSession.class)), with(equal(msg)),
					with(equal(time)), with(equal(signature)));
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
		expectGetSession(Collections.<MessageId, BdfDictionary>emptyMap(),
				sessionId);

		groupInvitationManager.respondToInvitation(contactId, sessionId, true);
	}

	@Test
	public void testAcceptInvitationWithSession() throws Exception {
		final boolean accept = true;
		SessionId sessionId = new SessionId(getRandomId());
		BdfDictionary state = BdfDictionary.of(new BdfEntry("f", "o"));
		Map<MessageId, BdfDictionary> states =
				Collections.singletonMap(storageMessage.getId(), state);

		expectRespondToInvitation(states, sessionId, accept);
		groupInvitationManager
				.respondToInvitation(contactId, sessionId, accept);
	}

	@Test
	public void testDeclineInvitationWithSession() throws Exception {
		final boolean accept = false;
		SessionId sessionId = new SessionId(getRandomId());
		BdfDictionary state = BdfDictionary.of(new BdfEntry("f", "o"));
		Map<MessageId, BdfDictionary> states =
				Collections.singletonMap(storageMessage.getId(), state);

		expectRespondToInvitation(states, sessionId, accept);
		groupInvitationManager
				.respondToInvitation(contactId, sessionId, accept);
	}

	@Test
	public void testRespondToInvitationWithGroupId() throws Exception {
		final boolean accept = true;
		final PrivateGroup g = context.mock(PrivateGroup.class);
		SessionId sessionId = new SessionId(privateGroup.getId().getBytes());
		BdfDictionary state = BdfDictionary.of(new BdfEntry("f", "o"));
		Map<MessageId, BdfDictionary> states =
				Collections.singletonMap(storageMessage.getId(), state);

		context.checking(new Expectations() {{
			oneOf(g).getId();
			will(returnValue(privateGroup.getId()));
		}});
		expectRespondToInvitation(states, sessionId, accept);
		groupInvitationManager.respondToInvitation(contactId, g, accept);
	}

	private void expectRespondToInvitation(
			final Map<MessageId, BdfDictionary> states,
			final SessionId sessionId, final boolean accept) throws Exception {
		expectGetSession(states, sessionId);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
		}});

		if (states.isEmpty()) return;
		assertEquals(1, states.size());

		final BdfDictionary state = states.values().iterator().next();
		context.checking(new Expectations() {{
			oneOf(sessionParser)
					.parseInviteeSession(contactGroup.getId(), state);
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
		SessionId sessionId = new SessionId(privateGroup.getId().getBytes());
		final BdfDictionary state = BdfDictionary.of(new BdfEntry("f", "o"));
		Map<MessageId, BdfDictionary> states =
				Collections.singletonMap(storageMessage.getId(), state);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(sessionParser).parsePeerSession(contactGroup.getId(), state);
			will(returnValue(peerSession));
			oneOf(peerEngine).onJoinAction(txn, peerSession);
			will(returnValue(peerSession));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});
		expectGetSession(states, sessionId);
		expectStoreSession(peerSession, storageMessage.getId());

		groupInvitationManager
				.revealRelationship(contactId, privateGroup.getId());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRevealRelationshipWithoutSession() throws Exception {
		SessionId sessionId = new SessionId(privateGroup.getId().getBytes());

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(db).endTransaction(txn);
		}});
		expectGetSession(Collections.<MessageId, BdfDictionary>emptyMap(),
				sessionId);

		groupInvitationManager
				.revealRelationship(contactId, privateGroup.getId());
	}

	@Test
	public void testGetInvitationMessages() throws Exception {
		final BdfDictionary query = new BdfDictionary();
		final BdfDictionary d1 = BdfDictionary.of(new BdfEntry("m1", "d"));
		final BdfDictionary d2 = BdfDictionary.of(new BdfEntry("m2", "d"));
		final Map<MessageId, BdfDictionary> results =	new HashMap<>();
		results.put(message.getId(), d1);
		results.put(storageMessage.getId(), d2);
		long time1 = 1L, time2 = 2L;
		final MessageMetadata meta1 =
				new MessageMetadata(INVITE, privateGroup.getId(), time1, true,
						true, true, true);
		final MessageMetadata meta2 =
				new MessageMetadata(JOIN, privateGroup.getId(), time2, true,
						true, true, true);
		final BdfList list1 = BdfList.of(1);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContact(txn, contactId);
			will(returnValue(contact));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(messageParser).getMessagesVisibleInUiQuery();
			will(returnValue(query));
			oneOf(clientHelper)
					.getMessageMetadataAsDictionary(txn, contactGroup.getId(),
							query);
			will(returnValue(results));
			// first message
			oneOf(messageParser).parseMetadata(d1);
			will(returnValue(meta1));
			oneOf(db).getMessageStatus(txn, contactId, message.getId());
			oneOf(clientHelper).getMessage(txn, message.getId());
			will(returnValue(message));
			oneOf(clientHelper).toList(message);
			will(returnValue(list1));
			oneOf(messageParser).parseInviteMessage(message, list1);
			// second message
			oneOf(messageParser).parseMetadata(d2);
			will(returnValue(meta2));
			oneOf(db).getMessageStatus(txn, contactId, storageMessage.getId());
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
			} else if (m.getId().equals(storageMessage.getId())) {
				assertTrue(m instanceof GroupInvitationResponse);
				assertEquals(time2, m.getTimestamp());
			}
		}
	}

	@Test
	public void testGetInvitations() throws Exception {
		final BdfDictionary query = new BdfDictionary();
		BdfDictionary d1 = BdfDictionary.of(new BdfEntry("m1", "d"));
		BdfDictionary d2 = BdfDictionary.of(new BdfEntry("m2", "d"));
		final Map<MessageId, BdfDictionary> results =	new HashMap<>();
		results.put(message.getId(), d1);
		results.put(storageMessage.getId(), d2);
		final BdfList list1 = BdfList.of(1);
		final BdfList list2 = BdfList.of(2);
		long time1 = 1L, time2 = 2L;
		final InviteMessage m1 =
				new InviteMessage(message.getId(), contactGroup.getId(),
						privateGroup.getId(), time1, "test", author,
						getRandomBytes(5), null, getRandomBytes(5));
		final InviteMessage m2 =
				new InviteMessage(message.getId(), contactGroup.getId(),
						privateGroup.getId(), time2, "test", author,
						getRandomBytes(5), null, getRandomBytes(5));
		final PrivateGroup g = context.mock(PrivateGroup.class);

		context.checking(new Expectations() {{
			oneOf(messageParser).getInvitesAvailableToAnswerQuery();
			will(returnValue(query));
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getContacts(txn);
			will(returnValue(Collections.singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(clientHelper)
					.getMessageMetadataAsDictionary(txn, contactGroup.getId(),
							query);
			will(returnValue(results));
			// message 1
			oneOf(clientHelper).getMessage(txn, message.getId());
			will(returnValue(message));
			oneOf(clientHelper).toList(message);
			will(returnValue(list1));
			oneOf(messageParser).parseInviteMessage(message, list1);
			will(returnValue(m1));
			oneOf(privateGroupFactory)
					.createPrivateGroup(m1.getGroupName(), m1.getCreator(),
							m1.getSalt());
			will(returnValue(g));
			// message 2
			oneOf(clientHelper).getMessage(txn, storageMessage.getId());
			will(returnValue(storageMessage));
			oneOf(clientHelper).toList(storageMessage);
			will(returnValue(list2));
			oneOf(messageParser).parseInviteMessage(storageMessage, list2);
			will(returnValue(m2));
			oneOf(privateGroupFactory)
					.createPrivateGroup(m2.getGroupName(), m2.getCreator(),
							m2.getSalt());
			will(returnValue(g));
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
		final SessionId sessionId =
				new SessionId(privateGroup.getId().getBytes());
		final BdfDictionary meta = BdfDictionary.of(new BdfEntry("m", "d"));
		final Map<MessageId, BdfDictionary> results = new HashMap<>();
		results.put(message.getId(), meta);

		expectGetSession(results, sessionId);
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(sessionParser)
					.parseCreatorSession(contactGroup.getId(), meta);
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
		final Contact contact2 =
				new Contact(new ContactId(2), author, author.getId(), true,
						true);
		final Contact contact3 =
				new Contact(new ContactId(3), author, author.getId(), true,
						true);
		final Collection<Contact> contacts = new ArrayList<>();
		contacts.add(contact);
		contacts.add(contact2);
		contacts.add(contact3);
		final MessageId mId2 = new MessageId(getRandomId());
		final MessageId mId3 = new MessageId(getRandomId());
		final BdfDictionary meta1 = BdfDictionary.of(new BdfEntry("m1", "d"));
		final BdfDictionary meta2 = BdfDictionary.of(new BdfEntry("m2", "d"));
		final BdfDictionary meta3 = BdfDictionary.of(new BdfEntry("m3", "d"));

		expectGetSession(
				Collections.singletonMap(storageMessage.getId(), meta1),
				new SessionId(privateGroup.getId().getBytes()));
		expectGetSession(
				Collections.singletonMap(mId2, meta2),
				new SessionId(privateGroup.getId().getBytes()));
		expectGetSession(
				Collections.singletonMap(mId3, meta3),
				new SessionId(privateGroup.getId().getBytes()));
		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact);
			will(returnValue(contactGroup));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact2);
			will(returnValue(contactGroup));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID, contact3);
			will(returnValue(contactGroup));
			// session 1
			oneOf(sessionParser).getRole(meta1);
			will(returnValue(Role.CREATOR));
			oneOf(sessionParser)
					.parseCreatorSession(contactGroup.getId(), meta1);
			will(returnValue(creatorSession));
			oneOf(creatorEngine).onLeaveAction(txn, creatorSession);
			will(returnValue(creatorSession));
			// session 2
			oneOf(sessionParser).getRole(meta2);
			will(returnValue(Role.INVITEE));
			oneOf(sessionParser)
					.parseInviteeSession(contactGroup.getId(), meta2);
			will(returnValue(inviteeSession));
			oneOf(inviteeEngine).onLeaveAction(txn, inviteeSession);
			will(returnValue(inviteeSession));
			// session 3
			oneOf(sessionParser).getRole(meta3);
			will(returnValue(Role.PEER));
			oneOf(sessionParser)
					.parsePeerSession(contactGroup.getId(), meta3);
			will(returnValue(peerSession));
			oneOf(peerEngine).onLeaveAction(txn, peerSession);
			will(returnValue(peerSession));
		}});
		expectStoreSession(creatorSession, storageMessage.getId());
		expectStoreSession(inviteeSession, mId2);
		expectStoreSession(peerSession, mId3);
		groupInvitationManager.removingGroup(txn, privateGroup.getId());
	}

}
