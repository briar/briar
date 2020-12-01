package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.ContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
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
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.ConversationRequest;
import org.briarproject.briar.api.conversation.DeletionResult;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.SharingInvitationItem;
import org.briarproject.briar.api.sharing.SharingManager;
import org.briarproject.briar.client.ConversationClientImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.briar.sharing.MessageType.ABORT;
import static org.briarproject.briar.sharing.MessageType.ACCEPT;
import static org.briarproject.briar.sharing.MessageType.DECLINE;
import static org.briarproject.briar.sharing.MessageType.INVITE;
import static org.briarproject.briar.sharing.MessageType.LEAVE;
import static org.briarproject.briar.sharing.State.SHARING;

@NotNullByDefault
abstract class SharingManagerImpl<S extends Shareable>
		extends ConversationClientImpl
		implements SharingManager<S>, OpenDatabaseHook, ContactHook,
		ClientVersioningHook {

	private final ClientVersioningManager clientVersioningManager;
	private final MessageParser<S> messageParser;
	private final SessionEncoder sessionEncoder;
	private final SessionParser sessionParser;
	private final ContactGroupFactory contactGroupFactory;
	private final ProtocolEngine<S> engine;
	private final InvitationFactory<S, ?> invitationFactory;

	SharingManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			MetadataParser metadataParser, MessageParser<S> messageParser,
			SessionEncoder sessionEncoder, SessionParser sessionParser,
			MessageTracker messageTracker,
			ContactGroupFactory contactGroupFactory, ProtocolEngine<S> engine,
			InvitationFactory<S, ?> invitationFactory) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.clientVersioningManager = clientVersioningManager;
		this.messageParser = messageParser;
		this.sessionEncoder = sessionEncoder;
		this.sessionParser = sessionParser;
		this.contactGroupFactory = contactGroupFactory;
		this.engine = engine;
		this.invitationFactory = invitationFactory;
	}

	protected abstract ClientId getClientId();

	protected abstract int getMajorVersion();

	protected abstract ClientId getShareableClientId();

	protected abstract int getShareableMajorVersion();

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		// Create a local group to indicate that we've set this client up
		Group localGroup = contactGroupFactory.createLocalGroup(getClientId(),
				getMajorVersion());
		if (db.containsGroup(txn, localGroup.getId())) return;
		db.addGroup(txn, localGroup);
		// Set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		db.addGroup(txn, g);
		Visibility client = clientVersioningManager.getClientVisibility(txn,
				c.getId(), getClientId(), getMajorVersion());
		db.setGroupVisibility(txn, c.getId(), g.getId(), client);
		// Attach the contact ID to the group
		clientHelper.setContactId(txn, g.getId(), c.getId());
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		// Remove the contact group (all messages will be removed with it)
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(getClientId(),
				getMajorVersion(), c);
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary d) throws DbException, FormatException {
		// Parse the metadata
		MessageMetadata meta = messageParser.parseMetadata(d);
		// Look up the session, if there is one
		SessionId sessionId = getSessionId(meta.getShareableId());
		StoredSession ss = getSession(txn, m.getGroupId(), sessionId);
		// Handle the message
		Session session;
		MessageId storageId;
		if (ss == null) {
			session = handleFirstMessage(txn, m, body, meta);
			storageId = createStorageId(txn, m.getGroupId());
		} else {
			session = handleMessage(txn, m, body, meta, ss.bdfSession);
			storageId = ss.storageId;
		}
		// Store the updated session
		storeSession(txn, storageId, session);
		return false;
	}

	/**
	 * Adds the given Group and initializes a session between us
	 * and the Contact c in state SHARING.
	 * If a session already exists, this does nothing.
	 */
	void preShareGroup(Transaction txn, Contact c, Group g)
			throws DbException, FormatException {
		// Return if a session already exists with the contact
		GroupId contactGroupId = getContactGroup(c).getId();
		StoredSession existingSession = getSession(txn, contactGroupId,
				getSessionId(g.getId()));
		if (existingSession != null) return;

		// Add the shareable's group
		db.addGroup(txn, g);

		// Apply the client's visibility
		Visibility client = clientVersioningManager.getClientVisibility(txn,
				c.getId(), getShareableClientId(), getShareableMajorVersion());
		db.setGroupVisibility(txn, c.getId(), g.getId(), client);

		// Initialize session in sharing state
		Session session = new Session(SHARING, contactGroupId, g.getId(),
				null, null, 0, 0);
		MessageId storageId = createStorageId(txn, contactGroupId);
		storeSession(txn, storageId, session);
	}

	private SessionId getSessionId(GroupId shareableId) {
		return new SessionId(shareableId.getBytes());
	}

	@Nullable
	private StoredSession getSession(Transaction txn, GroupId contactGroupId,
			SessionId sessionId) throws DbException, FormatException {
		BdfDictionary query = sessionParser.getSessionQuery(sessionId);
		Map<MessageId, BdfDictionary> results = clientHelper
				.getMessageMetadataAsDictionary(txn, contactGroupId, query);
		if (results.size() > 1) throw new DbException();
		if (results.isEmpty()) return null;
		return new StoredSession(results.keySet().iterator().next(),
				results.values().iterator().next());
	}

	private Session handleFirstMessage(Transaction txn, Message m, BdfList body,
			MessageMetadata meta) throws DbException, FormatException {
		GroupId shareableId = meta.getShareableId();
		MessageType type = meta.getMessageType();
		if (type == INVITE) {
			Session session = new Session(m.getGroupId(), shareableId);
			BdfDictionary d = sessionEncoder.encodeSession(session);
			return handleMessage(txn, m, body, meta, d);
		} else {
			throw new FormatException(); // Invalid first message
		}
	}

	private Session handleMessage(Transaction txn, Message m, BdfList body,
			MessageMetadata meta, BdfDictionary d)
			throws DbException, FormatException {
		MessageType type = meta.getMessageType();
		Session session = sessionParser.parseSession(m.getGroupId(), d);
		if (type == INVITE) {
			InviteMessage<S> invite = messageParser.parseInviteMessage(m, body);
			return engine.onInviteMessage(txn, session, invite);
		} else if (type == ACCEPT) {
			AcceptMessage accept = messageParser.parseAcceptMessage(m, body);
			return engine.onAcceptMessage(txn, session, accept);
		} else if (type == DECLINE) {
			DeclineMessage decline = messageParser.parseDeclineMessage(m, body);
			return engine.onDeclineMessage(txn, session, decline);
		} else if (type == LEAVE) {
			LeaveMessage leave = messageParser.parseLeaveMessage(m, body);
			return engine.onLeaveMessage(txn, session, leave);
		} else if (type == ABORT) {
			AbortMessage abort = messageParser.parseAbortMessage(m, body);
			return engine.onAbortMessage(txn, session, abort);
		} else {
			throw new AssertionError();
		}
	}

	private MessageId createStorageId(Transaction txn, GroupId g)
			throws DbException {
		Message m = clientHelper.createMessageForStoringMetadata(g);
		db.addLocalMessage(txn, m, new Metadata(), false, false);
		return m.getId();
	}

	private void storeSession(Transaction txn, MessageId storageId,
			Session session) throws DbException, FormatException {
		BdfDictionary d = sessionEncoder.encodeSession(session);
		clientHelper.mergeMessageMetadata(txn, storageId, d);
	}

	@Override
	public void sendInvitation(GroupId shareableId, ContactId contactId,
			@Nullable String text) throws DbException {
		SessionId sessionId = getSessionId(shareableId);
		Transaction txn = db.startTransaction(false);
		try {
			Contact contact = db.getContact(txn, contactId);
			if (!canBeShared(txn, shareableId, contact))
				// we might have received an invitation in the meantime
				return;
			// Look up the session, if there is one
			GroupId contactGroupId = getContactGroup(contact).getId();
			StoredSession ss = getSession(txn, contactGroupId, sessionId);
			// Create or parse the session
			Session session;
			MessageId storageId;
			if (ss == null) {
				// This is the first invite - create a new session
				session = new Session(contactGroupId, shareableId);
				storageId = createStorageId(txn, contactGroupId);
			} else {
				// We already have a session
				session = sessionParser
						.parseSession(contactGroupId, ss.bdfSession);
				storageId = ss.storageId;
			}
			// Handle the invite action
			session = engine.onInviteAction(txn, session, text);
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
	public void respondToInvitation(S s, Contact c, boolean accept)
			throws DbException {
		respondToInvitation(c.getId(), getSessionId(s.getId()), accept);
	}

	@Override
	public void respondToInvitation(ContactId c, SessionId id, boolean accept)
			throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			// Look up the session
			Contact contact = db.getContact(txn, c);
			GroupId contactGroupId = getContactGroup(contact).getId();
			StoredSession ss = getSession(txn, contactGroupId, id);
			if (ss == null) throw new IllegalArgumentException();
			// Parse the session
			Session session =
					sessionParser.parseSession(contactGroupId, ss.bdfSession);
			// Handle the accept or decline action
			if (accept) session = engine.onAcceptAction(txn, session);
			else session = engine.onDeclineAction(txn, session);
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
	public Collection<ConversationMessageHeader> getMessageHeaders(
			Transaction txn, ContactId c) throws DbException {
		try {
			Contact contact = db.getContact(txn, c);
			GroupId contactGroupId = getContactGroup(contact).getId();
			BdfDictionary query = messageParser.getMessagesVisibleInUiQuery();
			Map<MessageId, BdfDictionary> results = clientHelper
					.getMessageMetadataAsDictionary(txn, contactGroupId, query);
			Collection<ConversationMessageHeader> messages =
					new ArrayList<>(results.size());
			for (Entry<MessageId, BdfDictionary> e : results.entrySet()) {
				MessageId m = e.getKey();
				MessageMetadata meta =
						messageParser.parseMetadata(e.getValue());
				MessageStatus status = db.getMessageStatus(txn, c, m);
				MessageType type = meta.getMessageType();
				if (type == INVITE) {
					messages.add(parseInvitationRequest(txn, c, m,
							meta, status));
				} else if (type == ACCEPT) {
					messages.add(parseInvitationResponse(contactGroupId, m,
							meta, status, true));
				} else if (type == DECLINE) {
					messages.add(parseInvitationResponse(contactGroupId, m,
							meta, status, false));
				}
			}
			return messages;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private ConversationRequest<S> parseInvitationRequest(Transaction txn,
			ContactId c, MessageId m, MessageMetadata meta,
			MessageStatus status) throws DbException, FormatException {
		// Look up the invite message to get the details of the private group
		InviteMessage<S> invite = messageParser.getInviteMessage(txn, m);
		// Find out whether the shareable can be opened
		boolean canBeOpened = meta.wasAccepted() &&
				db.containsGroup(txn, invite.getShareableId());
		return invitationFactory
				.createInvitationRequest(meta.isLocal(), status.isSent(),
						status.isSeen(), meta.isRead(), invite, c,
						meta.isAvailableToAnswer(), canBeOpened,
						meta.getAutoDeleteTimer());
	}

	private InvitationResponse parseInvitationResponse(GroupId contactGroupId,
			MessageId m, MessageMetadata meta, MessageStatus status,
			boolean accept) {
		return invitationFactory.createInvitationResponse(m, contactGroupId,
				meta.getTimestamp(), meta.isLocal(), status.isSent(),
				status.isSeen(), meta.isRead(), accept, meta.getShareableId(),
				meta.getAutoDeleteTimer());
	}

	@Override
	public Collection<SharingInvitationItem> getInvitations()
			throws DbException {
		List<SharingInvitationItem> items = new ArrayList<>();
		BdfDictionary query = messageParser.getInvitesAvailableToAnswerQuery();
		Map<S, Collection<Contact>> sharers = new HashMap<>();
		Transaction txn = db.startTransaction(true);
		try {
			// get invitations from each contact
			for (Contact c : db.getContacts(txn)) {
				GroupId contactGroupId = getContactGroup(c).getId();
				Map<MessageId, BdfDictionary> results =
						clientHelper.getMessageMetadataAsDictionary(txn,
								contactGroupId, query);
				for (MessageId m : results.keySet()) {
					InviteMessage<S> invite =
							messageParser.getInviteMessage(txn, m);
					S s = invite.getShareable();
					if (sharers.containsKey(s)) {
						sharers.get(s).add(c);
					} else {
						Collection<Contact> contacts = new ArrayList<>();
						contacts.add(c);
						sharers.put(s, contacts);
					}
				}
			}
			// construct the invitation items
			for (Entry<S, Collection<Contact>> e : sharers.entrySet()) {
				S s = e.getKey();
				Collection<Contact> contacts = e.getValue();
				boolean subscribed = db.containsGroup(txn, s.getId());
				SharingInvitationItem invitation =
						new SharingInvitationItem(s, subscribed, contacts);
				items.add(invitation);
			}
			db.commitTransaction(txn);
			return items;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Collection<Contact> getSharedWith(GroupId g) throws DbException {
		return db.transactionWithResult(true, txn -> getSharedWith(txn, g));
	}

	@Override
	public Collection<Contact> getSharedWith(Transaction txn, GroupId g)
			throws DbException {
		// TODO report also pending invitations
		Collection<Contact> contacts = new ArrayList<>();
		for (Contact c : db.getContacts(txn)) {
			if (db.getGroupVisibility(txn, c.getId(), g) == SHARED)
				contacts.add(c);
		}
		return contacts;
	}

	@Override
	public boolean canBeShared(GroupId g, Contact c) throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			boolean canBeShared = canBeShared(txn, g, c);
			db.commitTransaction(txn);
			return canBeShared;
		} finally {
			db.endTransaction(txn);
		}
	}

	private boolean canBeShared(Transaction txn, GroupId g, Contact c)
			throws DbException {
		// The group can't be shared unless the contact supports the client
		Visibility client = clientVersioningManager.getClientVisibility(txn,
				c.getId(), getShareableClientId(), getShareableMajorVersion());
		if (client != SHARED) return false;
		GroupId contactGroupId = getContactGroup(c).getId();
		SessionId sessionId = getSessionId(g);
		try {
			StoredSession ss = getSession(txn, contactGroupId, sessionId);
			// If there's no session, we can share the group with the contact
			if (ss == null) return true;
			// If the session's in the right state, the contact can be invited
			Session session =
					sessionParser.parseSession(contactGroupId, ss.bdfSession);
			return session.getState().canInvite();
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	void removingShareable(Transaction txn, S shareable) throws DbException {
		SessionId sessionId = getSessionId(shareable.getId());
		// If we have any sessions in progress, tell the contacts we're leaving
		try {
			for (Contact c : db.getContacts(txn)) {
				// Look up the session for the contact, if there is one
				GroupId contactGroupId = getContactGroup(c).getId();
				StoredSession ss = getSession(txn, contactGroupId, sessionId);
				if (ss == null) continue; // No session for this contact
				// Let the engine perform a LEAVE action
				Session session = sessionParser
						.parseSession(contactGroupId, ss.bdfSession);
				session = engine.onLeaveAction(txn, session);
				// Store the updated session
				storeSession(txn, ss.storageId, session);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void onClientVisibilityChanging(Transaction txn, Contact c,
			Visibility v) throws DbException {
		// Apply the client's visibility to the contact group
		Group g = getContactGroup(c);
		db.setGroupVisibility(txn, c.getId(), g.getId(), v);
	}

	ClientVersioningHook getShareableClientVersioningHook() {
		return this::onShareableClientVisibilityChanging;
	}

	// Versioning hook for the shareable client
	private void onShareableClientVisibilityChanging(Transaction txn, Contact c,
			Visibility client) throws DbException {
		try {
			Collection<Group> shareables = db.getGroups(txn,
					getShareableClientId(), getShareableMajorVersion());
			Map<GroupId, Visibility> m = getPreferredVisibilities(txn, c);
			for (Group g : shareables) {
				Visibility preferred = m.get(g.getId());
				if (preferred == null) continue; // No session for this group
				// Apply min of preferred visibility and client's visibility
				Visibility min = Visibility.min(preferred, client);
				db.setGroupVisibility(txn, c.getId(), g.getId(), min);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private Map<GroupId, Visibility> getPreferredVisibilities(Transaction txn,
			Contact c) throws DbException, FormatException {
		GroupId contactGroupId = getContactGroup(c).getId();
		BdfDictionary query = sessionParser.getAllSessionsQuery();
		Map<MessageId, BdfDictionary> results = clientHelper
				.getMessageMetadataAsDictionary(txn, contactGroupId, query);
		Map<GroupId, Visibility> m = new HashMap<>();
		for (BdfDictionary d : results.values()) {
			Session s = sessionParser.parseSession(contactGroupId, d);
			m.put(s.getShareableId(), s.getState().getVisibility());
		}
		return m;
	}

	@FunctionalInterface
	private interface DeletableSessionRetriever {
		Map<GroupId, DeletableSession> getDeletableSessions(Transaction txn,
				GroupId contactGroup, Map<MessageId, BdfDictionary> metadata)
				throws DbException;
	}

	@FunctionalInterface
	private interface MessageDeletionChecker {
		/**
		 * This is called for all messages belonging to a session.
		 * It returns true if the given {@link MessageId} causes a problem
		 * so that the session can not be deleted.
		 */
		boolean causesProblem(MessageId messageId);
	}

	@Override
	public DeletionResult deleteAllMessages(Transaction txn, ContactId c)
			throws DbException {
		return deleteMessages(txn, c, (txn1, contactGroup, metadata) -> {
			// get all sessions and their states
			Map<GroupId, DeletableSession> sessions = new HashMap<>();
			for (BdfDictionary d : metadata.values()) {
				if (!sessionParser.isSession(d)) continue;
				Session session;
				try {
					session = sessionParser.parseSession(contactGroup, d);
				} catch (FormatException e) {
					throw new DbException(e);
				}
				sessions.put(session.getShareableId(),
						new DeletableSession(session.getState()));
			}
			return sessions;
		}, messageId -> false);
	}

	@Override
	public DeletionResult deleteMessages(Transaction txn, ContactId c,
			Set<MessageId> messageIds) throws DbException {
		return deleteMessages(txn, c, (txn1, g, metadata) -> {
			// get only sessions from given messageIds
			Map<GroupId, DeletableSession> sessions = new HashMap<>();
			for (MessageId messageId : messageIds) {
				BdfDictionary d = metadata.get(messageId);
				if (d == null) continue;  // throw new NoSuchMessageException()
				try {
					MessageMetadata messageMetadata =
							messageParser.parseMetadata(d);
					SessionId sessionId =
							getSessionId(messageMetadata.getShareableId());
					StoredSession ss = getSession(txn1, g, sessionId);
					if (ss == null) throw new DbException();
					Session session = sessionParser
							.parseSession(g, metadata.get(ss.storageId));
					sessions.put(session.getShareableId(),
							new DeletableSession(session.getState()));
				} catch (FormatException e) {
					throw new DbException(e);
				}
			}
			return sessions;
			// don't delete sessions if a message is not part of messageIds
		}, messageId -> !messageIds.contains(messageId));
	}

	private DeletionResult deleteMessages(Transaction txn, ContactId c,
			DeletableSessionRetriever retriever, MessageDeletionChecker checker)
			throws DbException {
		// get ID of the contact group
		GroupId g = getContactGroup(db.getContact(txn, c)).getId();

		// get metadata for all messages in the group
		// (these are sessions *and* protocol messages)
		Map<MessageId, BdfDictionary> metadata;
		try {
			metadata = clientHelper.getMessageMetadataAsDictionary(txn, g);
		} catch (FormatException e) {
			throw new DbException(e);
		}

		// get sessions and their states
		Map<GroupId, DeletableSession> sessions =
				retriever.getDeletableSessions(txn, g, metadata);

		// assign protocol messages to their sessions
		for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
			// skip all sessions, we are only interested in messages
			BdfDictionary d = entry.getValue();
			if (sessionParser.isSession(d)) continue;

			// parse message metadata and skip messages not visible in UI
			MessageMetadata m;
			try {
				m = messageParser.parseMetadata(d);
			} catch (FormatException e) {
				throw new DbException(e);
			}
			if (!m.isVisibleInConversation()) continue;

			// add visible messages to session
			DeletableSession session = sessions.get(m.getShareableId());
			if (session != null) session.messages.add(entry.getKey());
		}

		// get a set of all messages which were not ACKed by the contact
		Set<MessageId> notAcked = new HashSet<>();
		for (MessageStatus status : db.getMessageStatus(txn, c, g)) {
			if (!status.isSeen()) notAcked.add(status.getMessageId());
		}
		DeletionResult result = deleteCompletedSessions(txn, sessions.values(),
				notAcked, checker);
		recalculateGroupCount(txn, g);
		return result;
	}

	private DeletionResult deleteCompletedSessions(Transaction txn,
			Collection<DeletableSession> sessions, Set<MessageId> notAcked,
			MessageDeletionChecker checker) throws DbException {
		// find completed sessions to delete
		DeletionResult result = new DeletionResult();
		for (DeletableSession session : sessions) {
			if (session.state.isAwaitingResponse()) {
				result.addInvitationSessionInProgress();
				continue;
			}
			// we can only delete sessions
			// where delivery of all messages was confirmed (aka ACKed)
			boolean sessionDeletable = true;
			for (MessageId m : session.messages) {
				if (notAcked.contains(m) || checker.causesProblem(m)) {
					sessionDeletable = false;
					if (notAcked.contains(m))
						result.addInvitationSessionInProgress();
					if (checker.causesProblem(m))
						result.addInvitationNotAllSelected();
				}
			}
			if (sessionDeletable) {
				for (MessageId m : session.messages) {
					db.deleteMessage(txn, m);
					db.deleteMessageMetadata(txn, m);
				}
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
			Map<MessageId, BdfDictionary> results =
					clientHelper.getMessageMetadataAsDictionary(txn, g, query);
			return results.keySet();
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
