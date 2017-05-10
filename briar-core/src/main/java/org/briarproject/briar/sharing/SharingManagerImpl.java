package org.briarproject.briar.sharing;

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
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationMessage;
import org.briarproject.briar.api.sharing.InvitationRequest;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.SharingInvitationItem;
import org.briarproject.briar.api.sharing.SharingManager;
import org.briarproject.briar.client.ConversationClientImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.briar.sharing.MessageType.ABORT;
import static org.briarproject.briar.sharing.MessageType.ACCEPT;
import static org.briarproject.briar.sharing.MessageType.DECLINE;
import static org.briarproject.briar.sharing.MessageType.INVITE;
import static org.briarproject.briar.sharing.MessageType.LEAVE;
import static org.briarproject.briar.sharing.SharingConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.sharing.State.SHARING;

@NotNullByDefault
abstract class SharingManagerImpl<S extends Shareable>
		extends ConversationClientImpl
		implements SharingManager<S>, Client, AddContactHook,
		RemoveContactHook {

	private final MessageParser<S> messageParser;
	private final SessionEncoder sessionEncoder;
	private final SessionParser sessionParser;
	private final ContactGroupFactory contactGroupFactory;
	private final ProtocolEngine<S> engine;
	private final InvitationFactory<S, ?> invitationFactory;

	SharingManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			MetadataParser metadataParser, MessageParser<S> messageParser,
			SessionEncoder sessionEncoder, SessionParser sessionParser,
			MessageTracker messageTracker,
			ContactGroupFactory contactGroupFactory, ProtocolEngine<S> engine,
			InvitationFactory<S, ?> invitationFactory) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.messageParser = messageParser;
		this.sessionEncoder = sessionEncoder;
		this.sessionParser = sessionParser;
		this.contactGroupFactory = contactGroupFactory;
		this.engine = engine;
		this.invitationFactory = invitationFactory;
	}

	protected abstract ClientId getClientId();

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		// Ensure we've set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		try {
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
			clientHelper.mergeGroupMetadata(txn, g.getId(), meta);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		// remove the contact group (all messages will be removed with it)
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(getClientId(), c);
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

	protected void initializeSharedSession(Transaction txn, Contact c,
			S shareable) throws DbException, FormatException {
		GroupId contactGroupId = getContactGroup(c).getId();
		Session session =
				new Session(SHARING, contactGroupId, shareable.getId(), null,
						null, 0, 0);
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
		db.addLocalMessage(txn, m, new Metadata(), false);
		return m.getId();
	}

	private void storeSession(Transaction txn, MessageId storageId,
			Session session) throws DbException, FormatException {
		BdfDictionary d = sessionEncoder.encodeSession(session);
		clientHelper.mergeMessageMetadata(txn, storageId, d);
	}

	@Override
	public void sendInvitation(GroupId shareableId, ContactId contactId,
			@Nullable String message, long timestamp) throws DbException {
		SessionId sessionId = getSessionId(shareableId);
		Transaction txn = db.startTransaction(false);
		try {
			Contact contact = db.getContact(txn, contactId);
			if (!canBeShared(txn, shareableId, contact))
				throw new IllegalArgumentException();
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
			session = engine.onInviteAction(txn, session, message, timestamp);
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
					messages.add(
							parseInvitationRequest(txn, c, m, meta, status));
				} else if (type == ACCEPT) {
					messages.add(
							parseInvitationResponse(c, contactGroupId, m, meta,
									status, true));
				} else if (type == DECLINE) {
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

	private InvitationRequest parseInvitationRequest(Transaction txn,
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
						meta.isAvailableToAnswer(), canBeOpened);
	}

	private InvitationResponse parseInvitationResponse(ContactId c,
			GroupId contactGroupId, MessageId m, MessageMetadata meta,
			MessageStatus status, boolean accept)
			throws DbException, FormatException {
		return invitationFactory.createInvitationResponse(m, contactGroupId,
				meta.getTimestamp(), meta.isLocal(), status.isSent(),
				status.isSeen(), meta.isRead(), meta.getShareableId(), c,
				accept);
	}

	@Override
	public Collection<SharingInvitationItem> getInvitations()
			throws DbException {
		List<SharingInvitationItem> items =
				new ArrayList<SharingInvitationItem>();
		BdfDictionary query = messageParser.getInvitesAvailableToAnswerQuery();
		Map<S, Collection<Contact>> sharers =
				new HashMap<S, Collection<Contact>>();
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
						Collection<Contact> contacts = new ArrayList<Contact>();
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
		// TODO report also pending invitations
		Collection<Contact> contacts = new ArrayList<Contact>();
		Transaction txn = db.startTransaction(true);
		try {
			for (Contact c : db.getContacts(txn)) {
				if (db.getGroupVisibility(txn, c.getId(), g) == SHARED)
					contacts.add(c);
			}
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
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

	protected void removingShareable(Transaction txn, S shareable)
			throws DbException {
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

	private static class StoredSession {

		private final MessageId storageId;
		private final BdfDictionary bdfSession;

		private StoredSession(MessageId storageId, BdfDictionary bdfSession) {
			this.storageId = storageId;
			this.bdfSession = bdfSession;
		}
	}

}
