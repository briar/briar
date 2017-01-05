package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.AddContactHook;
import org.briarproject.bramble.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.PrivateGroupManager.PrivateGroupHook;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationItem;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;
import org.briarproject.briar.api.sharing.InvitationMessage;
import org.briarproject.briar.client.ConversationClientImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.briar.privategroup.invitation.CreatorState.START;
import static org.briarproject.briar.privategroup.invitation.GroupInvitationConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.privategroup.invitation.MessageType.ABORT;
import static org.briarproject.briar.privategroup.invitation.MessageType.INVITE;
import static org.briarproject.briar.privategroup.invitation.MessageType.JOIN;
import static org.briarproject.briar.privategroup.invitation.MessageType.LEAVE;
import static org.briarproject.briar.privategroup.invitation.Role.CREATOR;
import static org.briarproject.briar.privategroup.invitation.Role.INVITEE;
import static org.briarproject.briar.privategroup.invitation.Role.PEER;

@Immutable
@NotNullByDefault
class GroupInvitationManagerImpl extends ConversationClientImpl
		implements GroupInvitationManager, Client, AddContactHook,
		RemoveContactHook, PrivateGroupHook {

	private final ContactGroupFactory contactGroupFactory;
	private final PrivateGroupFactory privateGroupFactory;
	private final PrivateGroupManager privateGroupManager;
	private final MessageParser messageParser;
	private final SessionParser sessionParser;
	private final SessionEncoder sessionEncoder;
	private final ProtocolEngine<CreatorSession> creatorEngine;
	private final ProtocolEngine<InviteeSession> inviteeEngine;
	private final ProtocolEngine<PeerSession> peerEngine;

	@Inject
	GroupInvitationManagerImpl(DatabaseComponent db,
			ClientHelper clientHelper, MetadataParser metadataParser,
			MessageTracker messageTracker,
			ContactGroupFactory contactGroupFactory,
			PrivateGroupFactory privateGroupFactory,
			PrivateGroupManager privateGroupManager,
			MessageParser messageParser, SessionParser sessionParser,
			SessionEncoder sessionEncoder,
			ProtocolEngineFactory engineFactory) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.contactGroupFactory = contactGroupFactory;
		this.privateGroupFactory = privateGroupFactory;
		this.privateGroupManager = privateGroupManager;
		this.messageParser = messageParser;
		this.sessionParser = sessionParser;
		this.sessionEncoder = sessionEncoder;
		creatorEngine = engineFactory.createCreatorEngine();
		inviteeEngine = engineFactory.createInviteeEngine();
		peerEngine = engineFactory.createPeerEngine();
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		// Ensure we've set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		// Return if we've already set things up for this contact
		if (db.containsGroup(txn, g.getId())) return;
		// Store the group and share it with the contact
		db.addGroup(txn, g);
		db.setGroupVisibility(txn, c.getId(), g.getId(), SHARED);
		// Attach the contact ID to the group
		BdfDictionary meta = new BdfDictionary();
		meta.put(GROUP_KEY_CONTACT_ID, c.getId().getInt());
		try {
			clientHelper.mergeGroupMetadata(txn, g.getId(), meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
		// If the contact belongs to any private groups, create a peer session
		for (Group pg : db.getGroups(txn, PrivateGroupManager.CLIENT_ID)) {
			if (privateGroupManager.isMember(txn, pg.getId(), c.getAuthor()))
				addingMember(txn, pg.getId(), c);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		// Remove the contact group (all messages will be removed with it)
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID, c);
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary bdfMeta) throws DbException, FormatException {
		// Parse the metadata
		MessageMetadata meta = messageParser.parseMetadata(bdfMeta);
		// Look up the session, if there is one
		SessionId sessionId = getSessionId(meta.getPrivateGroupId());
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

	private SessionId getSessionId(GroupId privateGroupId) {
		return new SessionId(privateGroupId.getBytes());
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
		GroupId privateGroupId = meta.getPrivateGroupId();
		MessageType type = meta.getMessageType();
		if (type == INVITE) {
			InviteeSession session =
					new InviteeSession(m.getGroupId(), privateGroupId);
			return handleMessage(txn, m, body, type, session, inviteeEngine);
		} else if (type == JOIN) {
			PeerSession session =
					new PeerSession(m.getGroupId(), privateGroupId);
			return handleMessage(txn, m, body, type, session, peerEngine);
		} else {
			throw new FormatException(); // Invalid first message
		}
	}

	private Session handleMessage(Transaction txn, Message m, BdfList body,
			MessageMetadata meta, BdfDictionary bdfSession)
			throws DbException, FormatException {
		MessageType type = meta.getMessageType();
		Role role = sessionParser.getRole(bdfSession);
		if (role == CREATOR) {
			CreatorSession session = sessionParser
					.parseCreatorSession(m.getGroupId(), bdfSession);
			return handleMessage(txn, m, body, type, session, creatorEngine);
		} else if (role == INVITEE) {
			InviteeSession session = sessionParser
					.parseInviteeSession(m.getGroupId(), bdfSession);
			return handleMessage(txn, m, body, type, session, inviteeEngine);
		} else if (role == PEER) {
			PeerSession session = sessionParser
					.parsePeerSession(m.getGroupId(), bdfSession);
			return handleMessage(txn, m, body, type, session, peerEngine);
		} else {
			throw new AssertionError();
		}
	}

	private <S extends Session> S handleMessage(Transaction txn, Message m,
			BdfList body, MessageType type, S session, ProtocolEngine<S> engine)
			throws DbException, FormatException {
		if (type == INVITE) {
			InviteMessage invite = messageParser.parseInviteMessage(m, body);
			return engine.onInviteMessage(txn, session, invite);
		} else if (type == JOIN) {
			JoinMessage join = messageParser.parseJoinMessage(m, body);
			return engine.onJoinMessage(txn, session, join);
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
		db.addLocalMessage(txn, m, new Metadata(), false);
		return m.getId();
	}

	private void storeSession(Transaction txn, MessageId storageId,
			Session session) throws DbException, FormatException {
		BdfDictionary d = sessionEncoder.encodeSession(session);
		clientHelper.mergeMessageMetadata(txn, storageId, d);
	}

	@Override
	public void sendInvitation(GroupId privateGroupId, ContactId c,
			@Nullable String message, long timestamp, byte[] signature)
			throws DbException {
		SessionId sessionId = getSessionId(privateGroupId);
		Transaction txn = db.startTransaction(false);
		try {
			// Look up the session, if there is one
			Contact contact = db.getContact(txn, c);
			GroupId contactGroupId = getContactGroup(contact).getId();
			StoredSession ss = getSession(txn, contactGroupId, sessionId);
			// Create or parse the session
			CreatorSession session;
			MessageId storageId;
			if (ss == null) {
				// This is the first invite - create a new session
				session = new CreatorSession(contactGroupId, privateGroupId);
				storageId = createStorageId(txn, contactGroupId);
			} else {
				// An earlier invite was declined, so we already have a session
				session = sessionParser
						.parseCreatorSession(contactGroupId, ss.bdfSession);
				storageId = ss.storageId;
			}
			// Handle the invite action
			session = creatorEngine.onInviteAction(txn, session, message,
					timestamp, signature);
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
	public void respondToInvitation(ContactId c, PrivateGroup g, boolean accept)
			throws DbException {
		respondToInvitation(c, getSessionId(g.getId()), accept);
	}

	@Override
	public void respondToInvitation(ContactId c, SessionId sessionId,
			boolean accept) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			// Look up the session
			Contact contact = db.getContact(txn, c);
			GroupId contactGroupId = getContactGroup(contact).getId();
			StoredSession ss = getSession(txn, contactGroupId, sessionId);
			if (ss == null) throw new IllegalArgumentException();
			// Parse the session
			InviteeSession session = sessionParser
					.parseInviteeSession(contactGroupId, ss.bdfSession);
			// Handle the join or leave action
			if (accept) session = inviteeEngine.onJoinAction(txn, session);
			else session = inviteeEngine.onLeaveAction(txn, session);
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
	public void revealRelationship(ContactId c, GroupId g) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			// Look up the session
			Contact contact = db.getContact(txn, c);
			GroupId contactGroupId = getContactGroup(contact).getId();
			StoredSession ss = getSession(txn, contactGroupId, getSessionId(g));
			if (ss == null) throw new IllegalArgumentException();
			// Parse the session
			PeerSession session = sessionParser
					.parsePeerSession(contactGroupId, ss.bdfSession);
			// Handle the join action
			session = peerEngine.onJoinAction(txn, session);
			// Store the updated session
			storeSession(txn, ss.storageId, session);
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private <S extends Session> S handleAction(Transaction txn,
			LocalAction type, S session, ProtocolEngine<S> engine)
			throws DbException, FormatException {
		if (type == LocalAction.INVITE) {
			throw new IllegalArgumentException();
		} else if (type == LocalAction.JOIN) {
			return engine.onJoinAction(txn, session);
		} else if (type == LocalAction.LEAVE) {
			return engine.onLeaveAction(txn, session);
		} else if (type == LocalAction.MEMBER_ADDED) {
			return engine.onMemberAddedAction(txn, session);
		} else {
			throw new AssertionError();
		}
	}

	@Override
	public Collection<InvitationMessage> getInvitationMessages(ContactId c)
			throws DbException {
		List<InvitationMessage> messages;
		Transaction txn = db.startTransaction(true);
		try {
			Contact contact = db.getContact(txn, c);
			GroupId contactGroupId = getContactGroup(contact).getId();
			BdfDictionary query = messageParser.getMessagesVisibleInUiQuery();
			Map<MessageId, BdfDictionary> results = clientHelper
					.getMessageMetadataAsDictionary(txn, contactGroupId, query);
			messages = new ArrayList<InvitationMessage>(results.size());
			for (Entry<MessageId, BdfDictionary> e : results.entrySet()) {
				MessageId m = e.getKey();
				MessageMetadata meta =
						messageParser.parseMetadata(e.getValue());
				MessageStatus status = db.getMessageStatus(txn, c, m);
				MessageType type = meta.getMessageType();
				if (type == INVITE) {
					messages.add(parseInvitationRequest(txn, c, contactGroupId,
							m, meta, status));
				} else if (type == JOIN) {
					messages.add(
							parseInvitationResponse(c, contactGroupId, m, meta,
									status, true));
				} else if (type == LEAVE) {
					messages.add(
							parseInvitationResponse(c, contactGroupId, m, meta,
									status, false));
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

	private GroupInvitationRequest parseInvitationRequest(Transaction txn,
			ContactId c, GroupId contactGroupId, MessageId m,
			MessageMetadata meta, MessageStatus status)
			throws DbException, FormatException {
		SessionId sessionId = getSessionId(meta.getPrivateGroupId());
		// Look up the invite message to get the details of the private group
		InviteMessage invite = messageParser.getInviteMessage(txn, m);
		PrivateGroup pg = privateGroupFactory
				.createPrivateGroup(invite.getGroupName(), invite.getCreator(),
						invite.getSalt());
		// Find out whether the private group can be opened
		boolean canBeOpened = meta.wasAccepted() &&
				db.containsGroup(txn, invite.getPrivateGroupId());
		return new GroupInvitationRequest(m, contactGroupId,
				meta.getTimestamp(), meta.isLocal(), status.isSent(),
				status.isSeen(), meta.isRead(), sessionId, pg, c,
				invite.getMessage(), meta.isAvailableToAnswer(), canBeOpened);
	}

	private GroupInvitationResponse parseInvitationResponse(ContactId c,
			GroupId contactGroupId, MessageId m, MessageMetadata meta,
			MessageStatus status, boolean accept)
			throws DbException, FormatException {
		SessionId sessionId = getSessionId(meta.getPrivateGroupId());
		return new GroupInvitationResponse(m, contactGroupId,
				meta.getTimestamp(), meta.isLocal(), status.isSent(),
				status.isSeen(), meta.isRead(), sessionId,
				meta.getPrivateGroupId(), c, accept);
	}

	@Override
	public Collection<GroupInvitationItem> getInvitations() throws DbException {
		List<GroupInvitationItem> items = new ArrayList<GroupInvitationItem>();
		BdfDictionary query = messageParser.getInvitesAvailableToAnswerQuery();
		Transaction txn = db.startTransaction(true);
		try {
			// Look up the available invite messages for each contact
			for (Contact c : db.getContacts(txn)) {
				GroupId contactGroupId = getContactGroup(c).getId();
				Map<MessageId, BdfDictionary> results =
						clientHelper.getMessageMetadataAsDictionary(txn,
								contactGroupId, query);
				for (MessageId m : results.keySet())
					items.add(parseGroupInvitationItem(txn, c, m));
			}
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return items;
	}

	@Override
	public boolean isInvitationAllowed(Contact c, GroupId privateGroupId)
			throws DbException {
		GroupId contactGroupId = getContactGroup(c).getId();
		SessionId sessionId = getSessionId(privateGroupId);
		Transaction txn = db.startTransaction(true);
		try {
			StoredSession ss = getSession(txn, contactGroupId, sessionId);
			db.commitTransaction(txn);
			// If there's no session, the contact can be invited
			if (ss == null) return true;
			// If the session's in the start state, the contact can be invited
			CreatorSession session = sessionParser
					.parseCreatorSession(contactGroupId, ss.bdfSession);
			return session.getState() == START;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private GroupInvitationItem parseGroupInvitationItem(Transaction txn,
			Contact c, MessageId m) throws DbException, FormatException {
		InviteMessage invite = messageParser.getInviteMessage(txn, m);
		PrivateGroup privateGroup = privateGroupFactory.createPrivateGroup(
				invite.getGroupName(), invite.getCreator(), invite.getSalt());
		return new GroupInvitationItem(privateGroup, c);
	}

	@Override
	public void addingMember(Transaction txn, GroupId privateGroupId, Author a)
			throws DbException {
		// If the member is a contact, handle the add member action
		for (Contact c : db.getContactsByAuthorId(txn, a.getId()))
			addingMember(txn, privateGroupId, c);
	}

	private void addingMember(Transaction txn, GroupId privateGroupId,
			Contact c) throws DbException {
		try {
			// Look up the session for the contact, if there is one
			GroupId contactGroupId = getContactGroup(c).getId();
			SessionId sessionId = getSessionId(privateGroupId);
			StoredSession ss = getSession(txn, contactGroupId, sessionId);
			// Create or parse the session
			Session session;
			MessageId storageId;
			if (ss == null) {
				// If there's no session the contact must be a peer,
				// otherwise we would have exchanged invitation messages
				PeerSession peerSession =
						new PeerSession(contactGroupId, privateGroupId);
				// Handle the action
				session = peerEngine.onMemberAddedAction(txn, peerSession);
				storageId = createStorageId(txn, contactGroupId);
			} else {
				// Handle the action
				session = handleAction(txn, LocalAction.MEMBER_ADDED,
						contactGroupId, ss.bdfSession);
				storageId = ss.storageId;
			}
			// Store the updated session
			storeSession(txn, storageId, session);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void removingGroup(Transaction txn, GroupId privateGroupId)
			throws DbException {
		SessionId sessionId = getSessionId(privateGroupId);
		// If we have any sessions in progress, tell the contacts we're leaving
		try {
			for (Contact c : db.getContacts(txn)) {
				// Look up the session for the contact, if there is one
				GroupId contactGroupId = getContactGroup(c).getId();
				StoredSession ss = getSession(txn, contactGroupId, sessionId);
				if (ss == null) continue; // No session for this contact
				// Handle the action
				Session session = handleAction(txn, LocalAction.LEAVE,
						contactGroupId, ss.bdfSession);
				// Store the updated session
				storeSession(txn, ss.storageId, session);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private Session handleAction(Transaction txn, LocalAction a,
			GroupId contactGroupId, BdfDictionary bdfSession)
			throws DbException, FormatException {
		Role role = sessionParser.getRole(bdfSession);
		if (role == CREATOR) {
			CreatorSession session = sessionParser
					.parseCreatorSession(contactGroupId, bdfSession);
			return handleAction(txn, a, session, creatorEngine);
		} else if (role == INVITEE) {
			InviteeSession session = sessionParser
					.parseInviteeSession(contactGroupId, bdfSession);
			return handleAction(txn, a, session, inviteeEngine);
		} else if (role == PEER) {
			PeerSession session = sessionParser
					.parsePeerSession(contactGroupId, bdfSession);
			return handleAction(txn, a, session, peerEngine);
		} else {
			throw new AssertionError();
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
