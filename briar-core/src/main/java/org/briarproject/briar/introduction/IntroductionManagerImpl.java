package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
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
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.introduction.IntroductionMessage;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.introduction.Role;
import org.briarproject.briar.client.ConversationClientImpl;
import org.briarproject.briar.introduction.IntroducerSession.Introducee;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.introduction.Role.INTRODUCEE;
import static org.briarproject.briar.api.introduction.Role.INTRODUCER;
import static org.briarproject.briar.introduction.IntroducerState.START;
import static org.briarproject.briar.introduction.IntroductionConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.introduction.MessageType.ABORT;
import static org.briarproject.briar.introduction.MessageType.ACCEPT;
import static org.briarproject.briar.introduction.MessageType.ACTIVATE;
import static org.briarproject.briar.introduction.MessageType.AUTH;
import static org.briarproject.briar.introduction.MessageType.DECLINE;
import static org.briarproject.briar.introduction.MessageType.REQUEST;

@Immutable
@NotNullByDefault
class IntroductionManagerImpl extends ConversationClientImpl
		implements IntroductionManager, Client, ContactHook,
		ClientVersioningHook {

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
			IdentityManager identityManager) {
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
		this.localGroup =
				contactGroupFactory.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
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
		BdfDictionary meta = new BdfDictionary();
		meta.put(GROUP_KEY_CONTACT_ID, c.getId().getInt());
		try {
			clientHelper.mergeGroupMetadata(txn, g.getId(), meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
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
		Session session;
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
		ContactId introducerId = getContactId(txn, m.getGroupId());
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

	private <S extends Session> S handleMessage(Transaction txn, Message m,
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

	private ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException, FormatException {
		BdfDictionary meta =
				clientHelper.getGroupMetadataAsDictionary(txn, contactGroupId);
		return new ContactId(meta.getLong(GROUP_KEY_CONTACT_ID).intValue());
	}

	private MessageId createStorageId(Transaction txn) throws DbException {
		Message m = clientHelper
				.createMessageForStoringMetadata(localGroup.getId());
		db.addLocalMessage(txn, m, new Metadata(), false);
		return m.getId();
	}

	private void storeSession(Transaction txn, MessageId storageId,
			Session session) throws DbException {
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
		return session.getState() == START;
	}

	@Override
	public void makeIntroduction(Contact c1, Contact c2, @Nullable String msg,
			long timestamp) throws DbException {
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
			session = introducerEngine
					.onRequestAction(txn, session, msg, timestamp);
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
			long timestamp, boolean accept) throws DbException {
		Transaction txn = db.startTransaction(false);
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
				session = introduceeEngine
						.onAcceptAction(txn, session, timestamp);
			} else {
				session = introduceeEngine
						.onDeclineAction(txn, session, timestamp);
			}
			// Store the updated session
			storeSession(txn, ss.storageId, session);
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Collection<IntroductionMessage> getIntroductionMessages(ContactId c)
			throws DbException {
		List<IntroductionMessage> messages;
		Transaction txn = db.startTransaction(true);
		try {
			Contact contact = db.getContact(txn, c);
			GroupId contactGroupId = getContactGroup(contact).getId();
			BdfDictionary query = messageParser.getMessagesVisibleInUiQuery();
			Map<MessageId, BdfDictionary> results = clientHelper
					.getMessageMetadataAsDictionary(txn, contactGroupId, query);
			messages = new ArrayList<>(results.size());
			for (Entry<MessageId, BdfDictionary> e : results.entrySet()) {
				MessageId m = e.getKey();
				MessageMetadata meta =
						messageParser.parseMetadata(e.getValue());
				MessageStatus status = db.getMessageStatus(txn, c, m);
				StoredSession ss = getSession(txn, meta.getSessionId());
				if (ss == null) throw new AssertionError();
				MessageType type = meta.getMessageType();
				if (type == REQUEST) {
					messages.add(
							parseInvitationRequest(txn, contactGroupId, m,
									meta, status, ss.bdfSession));
				} else if (type == ACCEPT) {
					messages.add(
							parseInvitationResponse(contactGroupId, m, meta,
									status, ss.bdfSession, true));
				} else if (type == DECLINE) {
					messages.add(
							parseInvitationResponse(contactGroupId, m, meta,
									status, ss.bdfSession, false));
				}
			}
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return messages;
	}

	private IntroductionRequest parseInvitationRequest(Transaction txn,
			GroupId contactGroupId, MessageId m, MessageMetadata meta,
			MessageStatus status, BdfDictionary bdfSession)
			throws DbException, FormatException {
		Role role = sessionParser.getRole(bdfSession);
		SessionId sessionId;
		Author author;
		if (role == INTRODUCER) {
			IntroducerSession session =
					sessionParser.parseIntroducerSession(bdfSession);
			sessionId = session.getSessionId();
			if (contactGroupId.equals(session.getIntroduceeA().groupId)) {
				author = session.getIntroduceeB().author;
			} else {
				author = session.getIntroduceeA().author;
			}
		} else if (role == INTRODUCEE) {
			IntroduceeSession session = sessionParser
					.parseIntroduceeSession(contactGroupId, bdfSession);
			sessionId = session.getSessionId();
			author = session.getRemote().author;
		} else throw new AssertionError();
		Message msg = clientHelper.getMessage(txn, m);
		if (msg == null) throw new AssertionError();
		BdfList body = clientHelper.toList(msg);
		RequestMessage rm = messageParser.parseRequestMessage(msg, body);
		String message = rm.getMessage();
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		boolean contactExists = contactManager
				.contactExists(txn, rm.getAuthor().getId(),
						localAuthor.getId());

		return new IntroductionRequest(sessionId, m, contactGroupId,
				role, meta.getTimestamp(), meta.isLocal(),
				status.isSent(), status.isSeen(), meta.isRead(),
				author.getName(), false, message, !meta.isAvailableToAnswer(),
				contactExists);
	}

	private IntroductionResponse parseInvitationResponse(GroupId contactGroupId,
			MessageId m, MessageMetadata meta, MessageStatus status,
			BdfDictionary bdfSession, boolean accept) throws FormatException {
		Role role = sessionParser.getRole(bdfSession);
		SessionId sessionId;
		Author author;
		if (role == INTRODUCER) {
			IntroducerSession session =
					sessionParser.parseIntroducerSession(bdfSession);
			sessionId = session.getSessionId();
			if (contactGroupId.equals(session.getIntroduceeA().groupId)) {
				author = session.getIntroduceeB().author;
			} else {
				author = session.getIntroduceeA().author;
			}
		} else if (role == INTRODUCEE) {
			IntroduceeSession session = sessionParser
					.parseIntroduceeSession(contactGroupId, bdfSession);
			sessionId = session.getSessionId();
			author = session.getRemote().author;
		} else throw new AssertionError();
		return new IntroductionResponse(sessionId, m, contactGroupId,
				role, meta.getTimestamp(), meta.isLocal(), status.isSent(),
				status.isSeen(), meta.isRead(), author.getName(), accept);
	}

	private void removeSessionWithIntroducer(Transaction txn,
			Contact introducer) throws DbException {
		BdfDictionary query = sessionEncoder
				.getIntroduceeSessionsByIntroducerQuery(introducer.getAuthor());
		Map<MessageId, BdfDictionary> sessions;
		try {
			sessions = clientHelper
					.getMessageMetadataAsDictionary(txn, localGroup.getId(),
							query);
		} catch (FormatException e) {
			throw new DbException(e);
		}
		for (MessageId id : sessions.keySet()) {
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

	private static class StoredSession {

		private final MessageId storageId;
		private final BdfDictionary bdfSession;

		private StoredSession(MessageId storageId, BdfDictionary bdfSession) {
			this.storageId = storageId;
			this.bdfSession = bdfSession;
		}
	}

}
