package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.cleanup.CleanupHook;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.ContactManager.ContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.briar.api.autodelete.event.ConversationMessagesDeletedEvent;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.DeletionResult;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.introduction.Role;
import org.briarproject.briar.client.ConversationClientImpl;
import org.briarproject.briar.introduction.IntroducerSession.Introducee;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.api.introduction.Role.INTRODUCEE;
import static org.briarproject.briar.api.introduction.Role.INTRODUCER;
import static org.briarproject.briar.introduction.IntroduceeState.AWAIT_RESPONSES;
import static org.briarproject.briar.introduction.IntroduceeState.REMOTE_ACCEPTED;
import static org.briarproject.briar.introduction.IntroduceeState.REMOTE_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.A_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.B_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.START;
import static org.briarproject.briar.introduction.MessageType.ABORT;
import static org.briarproject.briar.introduction.MessageType.ACCEPT;
import static org.briarproject.briar.introduction.MessageType.ACTIVATE;
import static org.briarproject.briar.introduction.MessageType.AUTH;
import static org.briarproject.briar.introduction.MessageType.DECLINE;
import static org.briarproject.briar.introduction.MessageType.REQUEST;

@Immutable
@NotNullByDefault
class IntroductionManagerImpl extends ConversationClientImpl
		implements IntroductionManager, OpenDatabaseHook, ContactHook,
		ClientVersioningHook, CleanupHook {

	private final ClientVersioningManager clientVersioningManager;
	private final ContactGroupFactory contactGroupFactory;
	private final ContactManager contactManager;
	private final MessageParser messageParser;
	private final SessionEncoder sessionEncoder;
	private final SessionParser sessionParser;
	private final IntroducerProtocolEngine introducerEngine;
	private final IntroduceeProtocolEngine introduceeEngine;
	private final IntroductionCrypto crypto;
	private final IdentityManager identityManager;
	private final AuthorManager authorManager;

	private final Group localGroup;

	@Inject
	IntroductionManagerImpl(
			DatabaseComponent db,
			ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			MetadataParser metadataParser,
			MessageTracker messageTracker,
			ContactGroupFactory contactGroupFactory,
			ContactManager contactManager,
			MessageParser messageParser,
			SessionEncoder sessionEncoder,
			SessionParser sessionParser,
			IntroducerProtocolEngine introducerEngine,
			IntroduceeProtocolEngine introduceeEngine,
			IntroductionCrypto crypto,
			IdentityManager identityManager,
			AuthorManager authorManager) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.clientVersioningManager = clientVersioningManager;
		this.contactGroupFactory = contactGroupFactory;
		this.contactManager = contactManager;
		this.messageParser = messageParser;
		this.sessionEncoder = sessionEncoder;
		this.sessionParser = sessionParser;
		this.introducerEngine = introducerEngine;
		this.introduceeEngine = introduceeEngine;
		this.crypto = crypto;
		this.identityManager = identityManager;
		this.authorManager = authorManager;
		this.localGroup =
				contactGroupFactory.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		// Create a local group to store protocol sessions
		if (db.containsGroup(txn, localGroup.getId())) return;
		db.addGroup(txn, localGroup);
		// Set up groups for communication with any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		db.addGroup(txn, g);
		// Apply the client's visibility to the contact group
		Visibility client = clientVersioningManager.getClientVisibility(txn,
				c.getId(), CLIENT_ID, MAJOR_VERSION);
		db.setGroupVisibility(txn, c.getId(), g.getId(), client);
		// Attach the contact ID to the group
		clientHelper.setContactId(txn, g.getId(), c.getId());
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		removeSessionWithIntroducer(txn, c);
		abortOrRemoveSessionWithIntroducee(txn, c);

		// Remove the contact group (all messages will be removed with it)
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public void onClientVisibilityChanging(Transaction txn, Contact c,
			Visibility v) throws DbException {
		// Apply the client's visibility to the contact group
		Group g = getContactGroup(c);
		db.setGroupVisibility(txn, c.getId(), g.getId(), v);
	}

	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory
				.createContactGroup(CLIENT_ID, MAJOR_VERSION, c);
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary bdfMeta) throws DbException, FormatException {
		// Parse the metadata
		MessageMetadata meta = messageParser.parseMetadata(bdfMeta);
		// set the clean-up timer that will be started when message gets read
		long timer = meta.getAutoDeleteTimer();
		if (timer != NO_AUTO_DELETE_TIMER) {
			db.setCleanupTimerDuration(txn, m.getId(), timer);
		}
		// Look up the session, if there is one
		SessionId sessionId = meta.getSessionId();
		IntroduceeSession newIntroduceeSession = null;
		if (sessionId == null) {
			if (meta.getMessageType() != REQUEST) throw new AssertionError();
			newIntroduceeSession = createNewIntroduceeSession(txn, m, body);
			sessionId = newIntroduceeSession.getSessionId();
		}
		StoredSession ss = getSession(txn, sessionId);
		// Handle the message
		Session<?> session;
		MessageId storageId;
		if (ss == null) {
			if (meta.getMessageType() != REQUEST) throw new FormatException();
			if (newIntroduceeSession == null) throw new AssertionError();
			storageId = createStorageId(txn);
			session = handleMessage(txn, m, body, meta.getMessageType(),
					newIntroduceeSession, introduceeEngine);
		} else {
			storageId = ss.storageId;
			Role role = sessionParser.getRole(ss.bdfSession);
			if (role == INTRODUCER) {
				session = handleMessage(txn, m, body, meta.getMessageType(),
						sessionParser.parseIntroducerSession(ss.bdfSession),
						introducerEngine);
			} else if (role == INTRODUCEE) {
				session = handleMessage(txn, m, body, meta.getMessageType(),
						sessionParser.parseIntroduceeSession(m.getGroupId(),
								ss.bdfSession), introduceeEngine);
			} else throw new AssertionError();
		}
		// Store the updated session
		storeSession(txn, storageId, session);
		return false;
	}

	private IntroduceeSession createNewIntroduceeSession(Transaction txn,
			Message m, BdfList body) throws DbException, FormatException {
		ContactId introducerId = clientHelper.getContactId(txn, m.getGroupId());
		Author introducer = db.getContact(txn, introducerId).getAuthor();
		Author local = identityManager.getLocalAuthor(txn);
		Author remote = messageParser.parseRequestMessage(m, body).getAuthor();
		if (local.equals(remote)) throw new FormatException();
		SessionId sessionId = crypto.getSessionId(introducer, local, remote);
		boolean alice = crypto.isAlice(local.getId(), remote.getId());
		return IntroduceeSession
				.getInitial(m.getGroupId(), sessionId, introducer, alice,
						remote);
	}

	private <S extends Session<?>> S handleMessage(Transaction txn, Message m,
			BdfList body, MessageType type, S session, ProtocolEngine<S> engine)
			throws DbException, FormatException {
		if (type == REQUEST) {
			RequestMessage request = messageParser.parseRequestMessage(m, body);
			return engine.onRequestMessage(txn, session, request);
		} else if (type == ACCEPT) {
			AcceptMessage accept = messageParser.parseAcceptMessage(m, body);
			return engine.onAcceptMessage(txn, session, accept);
		} else if (type == DECLINE) {
			DeclineMessage decline = messageParser.parseDeclineMessage(m, body);
			return engine.onDeclineMessage(txn, session, decline);
		} else if (type == AUTH) {
			AuthMessage auth = messageParser.parseAuthMessage(m, body);
			return engine.onAuthMessage(txn, session, auth);
		} else if (type == ACTIVATE) {
			ActivateMessage activate =
					messageParser.parseActivateMessage(m, body);
			return engine.onActivateMessage(txn, session, activate);
		} else if (type == ABORT) {
			AbortMessage abort = messageParser.parseAbortMessage(m, body);
			return engine.onAbortMessage(txn, session, abort);
		} else {
			throw new AssertionError();
		}
	}

	@Nullable
	private StoredSession getSession(Transaction txn,
			@Nullable SessionId sessionId) throws DbException, FormatException {
		if (sessionId == null) return null;
		BdfDictionary query = sessionParser.getSessionQuery(sessionId);
		Map<MessageId, BdfDictionary> results = clientHelper
				.getMessageMetadataAsDictionary(txn, localGroup.getId(), query);
		if (results.size() > 1) throw new DbException();
		if (results.isEmpty()) return null;
		return new StoredSession(results.keySet().iterator().next(),
				results.values().iterator().next());
	}

	private MessageId createStorageId(Transaction txn) throws DbException {
		Message m = clientHelper
				.createMessageForStoringMetadata(localGroup.getId());
		db.addLocalMessage(txn, m, new Metadata(), false, false);
		return m.getId();
	}

	private void storeSession(Transaction txn, MessageId storageId,
			Session<?> session) throws DbException {
		BdfDictionary d;
		if (session.getRole() == INTRODUCER) {
			d = sessionEncoder
					.encodeIntroducerSession((IntroducerSession) session);
		} else if (session.getRole() == INTRODUCEE) {
			d = sessionEncoder
					.encodeIntroduceeSession((IntroduceeSession) session);
		} else {
			throw new AssertionError();
		}
		try {
			clientHelper.mergeMessageMetadata(txn, storageId, d);
		} catch (FormatException e) {
			throw new AssertionError();
		}
	}

	@Override
	public boolean canIntroduce(Contact c1, Contact c2) throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			boolean can = canIntroduce(txn, c1, c2);
			db.commitTransaction(txn);
			return can;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private boolean canIntroduce(Transaction txn, Contact c1, Contact c2)
			throws DbException, FormatException {
		// Look up the session, if there is one
		Author introducer = identityManager.getLocalAuthor(txn);
		SessionId sessionId =
				crypto.getSessionId(introducer, c1.getAuthor(),
						c2.getAuthor());
		StoredSession ss = getSession(txn, sessionId);
		if (ss == null) return true;
		IntroducerSession session =
				sessionParser.parseIntroducerSession(ss.bdfSession);
		return session.getState().isComplete();
	}

	@Override
	public void makeIntroduction(Contact c1, Contact c2, @Nullable String text)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			// Look up the session, if there is one
			Author introducer = identityManager.getLocalAuthor(txn);
			SessionId sessionId =
					crypto.getSessionId(introducer, c1.getAuthor(),
							c2.getAuthor());
			StoredSession ss = getSession(txn, sessionId);
			// Create or parse the session
			IntroducerSession session;
			MessageId storageId;
			if (ss == null) {
				// This is the first request - create a new session
				GroupId groupId1 = getContactGroup(c1).getId();
				GroupId groupId2 = getContactGroup(c2).getId();
				boolean alice = crypto.isAlice(c1.getAuthor().getId(),
						c2.getAuthor().getId());
				// use fixed deterministic roles for the introducees
				session = new IntroducerSession(sessionId,
						alice ? groupId1 : groupId2,
						alice ? c1.getAuthor() : c2.getAuthor(),
						alice ? groupId2 : groupId1,
						alice ? c2.getAuthor() : c1.getAuthor()
				);
				storageId = createStorageId(txn);
			} else {
				// An earlier request exists, so we already have a session
				session = sessionParser.parseIntroducerSession(ss.bdfSession);
				storageId = ss.storageId;
			}
			// Handle the request action
			session = introducerEngine.onRequestAction(txn, session, text);
			// Store the updated session
			storeSession(txn, storageId, session);
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void respondToIntroduction(ContactId contactId, SessionId sessionId,
			boolean accept) throws DbException {
		respondToIntroduction(contactId, sessionId, accept, false);
	}

	private void respondToIntroduction(ContactId contactId, SessionId sessionId,
			boolean accept, boolean isAutoDecline) throws DbException {
		db.transaction(false,
				txn -> respondToIntroduction(txn, contactId, sessionId, accept,
						isAutoDecline));
	}

	private void respondToIntroduction(Transaction txn, ContactId contactId,
			SessionId sessionId, boolean accept, boolean isAutoDecline)
			throws DbException {
		try {
			// Look up the session
			StoredSession ss = getSession(txn, sessionId);
			if (ss == null) {
				// Actions from the UI may be based on stale information.
				// The contact might just have been deleted, for example.
				// Throwing a DbException here aborts gracefully.
				throw new DbException();
			}
			// Parse the session
			Contact contact = db.getContact(txn, contactId);
			GroupId contactGroupId = getContactGroup(contact).getId();
			IntroduceeSession session = sessionParser
					.parseIntroduceeSession(contactGroupId, ss.bdfSession);
			// Handle the join or leave action
			if (accept) {
				session = introduceeEngine.onAcceptAction(txn, session);
			} else {
				session = introduceeEngine
						.onDeclineAction(txn, session, isAutoDecline);
			}
			// Store the updated session
			storeSession(txn, ss.storageId, session);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<ConversationMessageHeader> getMessageHeaders(
			Transaction txn, ContactId c) throws DbException {
		try {
			Contact contact = db.getContact(txn, c);
			GroupId contactGroupId = getContactGroup(contact).getId();
			BdfDictionary query = messageParser.getMessagesVisibleInUiQuery();
			Map<MessageId, BdfDictionary> results = clientHelper
					.getMessageMetadataAsDictionary(txn, contactGroupId, query);
			List<ConversationMessageHeader> messages =
					new ArrayList<>(results.size());
			Map<AuthorId, AuthorInfo> authorInfos = new HashMap<>();
			for (Entry<MessageId, BdfDictionary> e : results.entrySet()) {
				MessageId m = e.getKey();
				MessageMetadata meta =
						messageParser.parseMetadata(e.getValue());
				MessageStatus status = db.getMessageStatus(txn, c, m);
				StoredSession ss = getSession(txn, meta.getSessionId());
				if (ss == null) throw new AssertionError();
				MessageType type = meta.getMessageType();
				if (type == REQUEST) {
					messages.add(parseInvitationRequest(txn, contactGroupId, m,
							meta, status, meta.getSessionId(), authorInfos));
				} else if (type == ACCEPT) {
					messages.add(parseInvitationResponse(txn, contactGroupId, m,
							meta, status, ss.bdfSession, authorInfos, true));
				} else if (type == DECLINE) {
					messages.add(parseInvitationResponse(txn, contactGroupId, m,
							meta, status, ss.bdfSession, authorInfos, false));
				}
			}
			return messages;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private IntroductionRequest parseInvitationRequest(Transaction txn,
			GroupId contactGroupId, MessageId m, MessageMetadata meta,
			MessageStatus status, SessionId sessionId,
			Map<AuthorId, AuthorInfo> authorInfos)
			throws DbException, FormatException {
		Message msg = clientHelper.getMessage(txn, m);
		BdfList body = clientHelper.toList(msg);
		RequestMessage rm = messageParser.parseRequestMessage(msg, body);
		String text = rm.getText();
		Author author = rm.getAuthor();
		AuthorInfo authorInfo = authorInfos.get(author.getId());
		if (authorInfo == null) {
			authorInfo = authorManager.getAuthorInfo(txn, author.getId());
			authorInfos.put(author.getId(), authorInfo);
		}
		return new IntroductionRequest(m, contactGroupId, meta.getTimestamp(),
				meta.isLocal(), meta.isRead(), status.isSent(), status.isSeen(),
				sessionId, author, text, !meta.isAvailableToAnswer(),
				authorInfo, rm.getAutoDeleteTimer());
	}

	private IntroductionResponse parseInvitationResponse(Transaction txn,
			GroupId contactGroupId, MessageId m, MessageMetadata meta,
			MessageStatus status, BdfDictionary bdfSession,
			Map<AuthorId, AuthorInfo> authorInfos, boolean accept)
			throws FormatException, DbException {
		Role role = sessionParser.getRole(bdfSession);
		SessionId sessionId;
		Author author;
		boolean canSucceed;
		if (role == INTRODUCER) {
			IntroducerSession session =
					sessionParser.parseIntroducerSession(bdfSession);
			sessionId = session.getSessionId();
			if (contactGroupId.equals(session.getIntroduceeA().groupId)) {
				author = session.getIntroduceeB().author;
			} else {
				author = session.getIntroduceeA().author;
			}
			IntroducerState s = session.getState();
			canSucceed = s != START && s != A_DECLINED && s != B_DECLINED;
		} else if (role == INTRODUCEE) {
			IntroduceeSession session = sessionParser
					.parseIntroduceeSession(contactGroupId, bdfSession);
			sessionId = session.getSessionId();
			author = session.getRemote().author;
			IntroduceeState s = session.getState();
			canSucceed = s != IntroduceeState.START && s != REMOTE_DECLINED;
		} else throw new AssertionError();
		AuthorInfo authorInfo = authorInfos.get(author.getId());
		if (authorInfo == null) {
			authorInfo = authorManager.getAuthorInfo(txn, author.getId());
			authorInfos.put(author.getId(), authorInfo);
		}
		return new IntroductionResponse(m, contactGroupId, meta.getTimestamp(),
				meta.isLocal(), meta.isRead(), status.isSent(), status.isSeen(),
				sessionId, accept, author, authorInfo, role, canSucceed,
				meta.getAutoDeleteTimer(), meta.isAutoDecline());
	}

	private void removeSessionWithIntroducer(Transaction txn,
			Contact introducer) throws DbException {
		BdfDictionary query = sessionEncoder
				.getIntroduceeSessionsByIntroducerQuery(introducer.getAuthor());
		Collection<MessageId> sessionIds;
		try {
			sessionIds = clientHelper.getMessageIds(txn, localGroup.getId(),
					query);
		} catch (FormatException e) {
			throw new DbException(e);
		}
		for (MessageId id : sessionIds) {
			db.removeMessage(txn, id);
		}
	}

	private void abortOrRemoveSessionWithIntroducee(Transaction txn,
			Contact c) throws DbException {
		BdfDictionary query = sessionEncoder.getIntroducerSessionsQuery();
		Map<MessageId, BdfDictionary> sessions;
		try {
			sessions = clientHelper
					.getMessageMetadataAsDictionary(txn, localGroup.getId(),
							query);
		} catch (FormatException e) {
			throw new DbException();
		}
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		for (Entry<MessageId, BdfDictionary> session : sessions.entrySet()) {
			IntroducerSession s;
			try {
				s = sessionParser.parseIntroducerSession(session.getValue());
			} catch (FormatException e) {
				throw new DbException();
			}
			if (s.getIntroduceeA().author.equals(c.getAuthor())) {
				abortOrRemoveSessionWithIntroducee(txn, s, session.getKey(),
						s.getIntroduceeB(), localAuthor);
			} else if (s.getIntroduceeB().author.equals(c.getAuthor())) {
				abortOrRemoveSessionWithIntroducee(txn, s, session.getKey(),
						s.getIntroduceeA(), localAuthor);
			}
		}
	}

	private void abortOrRemoveSessionWithIntroducee(Transaction txn,
			IntroducerSession s, MessageId storageId, Introducee i,
			LocalAuthor localAuthor) throws DbException {
		if (db.containsContact(txn, i.author.getId(), localAuthor.getId())) {
			IntroducerSession session =
					introducerEngine.onIntroduceeRemoved(txn, i, s);
			storeSession(txn, storageId, session);
		} else {
			db.removeMessage(txn, storageId);
		}
	}

	@Override
	public void deleteMessages(Transaction txn, GroupId g,
			Collection<MessageId> messageIds) throws DbException {
		ContactId c;
		Map<SessionId, DeletableSession> sessions = new HashMap<>();
		try {
			// get the ContactId from the given GroupId
			c = clientHelper.getContactId(txn, g);
			// get sessions for all messages to be deleted
			for (MessageId messageId : messageIds) {
				BdfDictionary d = clientHelper
						.getMessageMetadataAsDictionary(txn, messageId);
				MessageMetadata messageMetadata =
						messageParser.parseMetadata(d);
				SessionId sessionId = messageMetadata.getSessionId();
				DeletableSession deletableSession =
						sessions.get(sessionId);
				if (deletableSession == null) {
					StoredSession ss = getSession(txn, sessionId);
					if (ss == null) throw new DbException();
					Role role = sessionParser.getRole(ss.bdfSession);
					Session session;
					if (role == INTRODUCER) {
						session = sessionParser
								.parseIntroducerSession(ss.bdfSession);
					} else if (role == INTRODUCEE) {
						session = sessionParser
								.parseIntroduceeSession(g, ss.bdfSession);
					} else throw new AssertionError();
					deletableSession = new DeletableSession(session.getState());
					sessions.put(sessionId, deletableSession);
				}
				deletableSession.messages.add(messageId);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}

		// delete given visible messages in sessions and auto-respond before
		for (Entry<SessionId, DeletableSession> entry : sessions.entrySet()) {
			DeletableSession session = entry.getValue();
			// decline invitee sessions waiting for a response before
			if (session.state instanceof IntroduceeState) {
				IntroduceeState introduceeState =
						(IntroduceeState) session.state;
				if (introduceeState == AWAIT_RESPONSES ||
						introduceeState == REMOTE_DECLINED ||
						introduceeState == REMOTE_ACCEPTED) {
					respondToIntroduction(txn, c, entry.getKey(), false, true);
				}
			}
			for (MessageId m : session.messages) {
				db.deleteMessage(txn, m);
				db.deleteMessageMetadata(txn, m);
			}
		}
		recalculateGroupCount(txn, g);

		txn.attach(new ConversationMessagesDeletedEvent(c, messageIds));
	}

	@FunctionalInterface
	private interface MessageRetriever {
		/**
		 * Returns a set of messages that should be deleted.
		 * These must be a subset of the given set of all messages.
		 */
		Set<MessageId> getMessages(Set<MessageId> allMessages);
	}

	@Override
	public DeletionResult deleteAllMessages(Transaction txn, ContactId c)
			throws DbException {
		return deleteMessages(txn, c, allMessages -> allMessages);
	}

	@Override
	public DeletionResult deleteMessages(Transaction txn, ContactId c,
			Set<MessageId> messageIds) throws DbException {
		return deleteMessages(txn, c, allMessages -> messageIds);
	}

	private DeletionResult deleteMessages(Transaction txn, ContactId c,
			MessageRetriever retriever) throws DbException {
		// get ID of the contact group
		GroupId g = getContactGroup(db.getContact(txn, c)).getId();

		// get metadata for all messages in the group
		Map<MessageId, BdfDictionary> messages;
		try {
			messages = clientHelper.getMessageMetadataAsDictionary(txn, g);
		} catch (FormatException e) {
			throw new DbException(e);
		}

		// get messages to be deleted
		Set<MessageId> selected = retriever.getMessages(messages.keySet());

		// get sessions for selected messages
		Map<SessionId, DeletableSession> sessions = new HashMap<>();
		for (MessageId id : selected) {
			BdfDictionary d = messages.get(id);
			if (d == null) continue;  // throw new NoSuchMessageException()
			MessageMetadata m;
			try {
				m = messageParser.parseMetadata(d);
			} catch (FormatException e) {
				throw new DbException(e);
			}
			if (m.getSessionId() == null) {
				// This can only be an unhandled REQUEST message.
				// Its session is created and stored in incomingMessage(),
				// and getMessageMetadata() only returns delivered messages,
				// so the session ID should have been assigned.
				throw new AssertionError("missing session ID");
			}
			// get session from map or database
			DeletableSession session = sessions.get(m.getSessionId());
			if (session == null) {
				session = getDeletableSession(txn, g, m.getSessionId());
				sessions.put(m.getSessionId(), session);
			}
			session.messages.add(id);
		}

		// assign other protocol messages to their sessions
		for (Entry<MessageId, BdfDictionary> entry : messages.entrySet()) {
			// we handled selected messages above
			if (selected.contains(entry.getKey())) continue;

			MessageMetadata m;
			try {
				m = messageParser.parseMetadata(entry.getValue());
			} catch (FormatException e) {
				throw new DbException(e);
			}
			if (!m.isVisibleInConversation()) continue;
			if (m.getSessionId() == null) {
				// This can only be an unhandled REQUEST message.
				// Its session is created and stored in incomingMessage(),
				// and getMessageMetadata() only returns delivered messages,
				// so the session ID should have been assigned.
				throw new AssertionError("missing session ID");
			}
			// get session from map or database
			DeletableSession session = sessions.get(m.getSessionId());
			if (session == null) continue;  // not a session of a selected msg
			session.messages.add(entry.getKey());
		}

		// get a set of all messages which were not ACKed by the contact
		Set<MessageId> notAcked = new HashSet<>();
		for (MessageStatus status : db.getMessageStatus(txn, c, g)) {
			if (!status.isSeen()) notAcked.add(status.getMessageId());
		}
		DeletionResult result =
				deleteCompletedSessions(txn, sessions, notAcked, selected);
		recalculateGroupCount(txn, g);
		return result;
	}

	private DeletableSession getDeletableSession(Transaction txn,
			GroupId introducerGroupId, SessionId sessionId) throws DbException {
		try {
			StoredSession ss = getSession(txn, sessionId);
			if (ss == null) throw new AssertionError();
			Session<?> s;
			Role role = sessionParser.getRole(ss.bdfSession);
			if (role == INTRODUCER) {
				s = sessionParser.parseIntroducerSession(ss.bdfSession);
			} else if (role == INTRODUCEE) {
				s = sessionParser.parseIntroduceeSession(introducerGroupId,
						ss.bdfSession);
			} else throw new AssertionError();
			return new DeletableSession(s.getState());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private DeletionResult deleteCompletedSessions(Transaction txn,
			Map<SessionId, DeletableSession> sessions, Set<MessageId> notAcked,
			Set<MessageId> selected) throws DbException {
		// find completed sessions to delete
		DeletionResult result = new DeletionResult();
		for (DeletableSession session : sessions.values()) {
			if (!session.state.isComplete()) {
				result.addIntroductionSessionInProgress();
				continue;
			}
			// we can only delete sessions
			// where delivery of all messages was confirmed (aka ACKed)
			boolean sessionDeletable = true;
			for (MessageId m : session.messages) {
				if (notAcked.contains(m) || !selected.contains(m)) {
					sessionDeletable = false;
					if (notAcked.contains(m))
						result.addIntroductionSessionInProgress();
					if (!selected.contains(m))
						result.addIntroductionNotAllSelected();
				}
			}
			// delete messages of session, if all were ACKed
			if (sessionDeletable) {
				for (MessageId m : session.messages) {
					db.deleteMessage(txn, m);
					db.deleteMessageMetadata(txn, m);
				}
				// we can not delete the session as it might get restarted
				// and then needs the previous MessageIds
			}
		}
		return result;
	}

	@Override
	public Set<MessageId> getMessageIds(Transaction txn, ContactId c)
			throws DbException {
		GroupId g = getContactGroup(db.getContact(txn, c)).getId();
		BdfDictionary query = messageParser.getMessagesVisibleInUiQuery();
		try {
			return new HashSet<>(clientHelper.getMessageIds(txn, g, query));
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void recalculateGroupCount(Transaction txn, GroupId g)
			throws DbException {
		BdfDictionary query = messageParser.getMessagesVisibleInUiQuery();
		Map<MessageId, BdfDictionary> results;
		try {
			results =
					clientHelper.getMessageMetadataAsDictionary(txn, g, query);
		} catch (FormatException e) {
			throw new DbException(e);
		}
		int msgCount = 0;
		int unreadCount = 0;
		for (Entry<MessageId, BdfDictionary> entry : results.entrySet()) {
			MessageMetadata meta;
			try {
				meta = messageParser.parseMetadata(entry.getValue());
			} catch (FormatException e) {
				throw new DbException(e);
			}
			msgCount++;
			if (!meta.isRead()) unreadCount++;
		}
		messageTracker.resetGroupCount(txn, g, msgCount, unreadCount);
	}

	private static class StoredSession {

		private final MessageId storageId;
		private final BdfDictionary bdfSession;

		private StoredSession(MessageId storageId, BdfDictionary bdfSession) {
			this.storageId = storageId;
			this.bdfSession = bdfSession;
		}
	}

	private static class DeletableSession {

		private final State state;
		private final List<MessageId> messages = new ArrayList<>();

		private DeletableSession(State state) {
			this.state = state;
		}
	}

}
