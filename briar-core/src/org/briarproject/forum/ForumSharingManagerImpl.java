package org.briarproject.forum;

import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.Client;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager.AddContactHook;
import org.briarproject.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.Event;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumFactory;
import org.briarproject.api.forum.ForumInvitationMessage;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.clients.ProtocolEngine.StateUpdate;
import static org.briarproject.api.forum.ForumConstants.CONTACT_ID;
import static org.briarproject.api.forum.ForumConstants.FORUM_ID;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.IS_SHARER;
import static org.briarproject.api.forum.ForumConstants.LOCAL;
import static org.briarproject.api.forum.ForumConstants.READ;
import static org.briarproject.api.forum.ForumConstants.SESSION_ID;
import static org.briarproject.api.forum.ForumConstants.SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.forum.ForumConstants.STATE;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_SHARED_FORUM;
import static org.briarproject.api.forum.ForumConstants.TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TASK_REMOVE_FORUM_FROM_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TASK_SHARE_FORUM;
import static org.briarproject.api.forum.ForumConstants.TASK_UNSHARE_FORUM_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TASK_UNSHARE_FORUM_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TIME;
import static org.briarproject.api.forum.ForumConstants.TO_BE_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TYPE;
import static org.briarproject.api.forum.ForumManager.RemoveForumHook;
import static org.briarproject.api.forum.ForumSharingMessage.BaseMessage;
import static org.briarproject.api.forum.ForumSharingMessage.Invitation;
import static org.briarproject.forum.ForumSharingSessionState.fromBdfDictionary;
import static org.briarproject.forum.SharerSessionState.Action;

class ForumSharingManagerImpl extends BdfIncomingMessageHook
		implements ForumSharingManager, Client, RemoveForumHook,
		AddContactHook, RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"cd11a5d04dccd9e2931d6fc3df456313"
					+ "63bb3e9d9d0e9405fccdb051f41f5449"));

	private static final Logger LOG =
			Logger.getLogger(ForumSharingManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final ForumManager forumManager;
	private final MessageQueueManager messageQueueManager;
	private final MetadataEncoder metadataEncoder;
	private final SecureRandom random;
	private final PrivateGroupFactory privateGroupFactory;
	private final ForumFactory forumFactory;
	private final Clock clock;
	private final Group localGroup;

	@Inject
	ForumSharingManagerImpl(DatabaseComponent db, ForumManager forumManager,
			MessageQueueManager messageQueueManager, ClientHelper clientHelper,
			MetadataParser metadataParser, MetadataEncoder metadataEncoder,
			SecureRandom random, PrivateGroupFactory privateGroupFactory,
			ForumFactory forumFactory, Clock clock) {

		super(clientHelper, metadataParser);
		this.db = db;
		this.forumManager = forumManager;
		this.messageQueueManager = messageQueueManager;
		this.metadataEncoder = metadataEncoder;
		this.random = random;
		this.privateGroupFactory = privateGroupFactory;
		this.forumFactory = forumFactory;
		this.clock = clock;
		localGroup = privateGroupFactory.createLocalGroup(getClientId());
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		db.addGroup(txn, localGroup);
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
			db.setVisibleToContact(txn, c.getId(), g.getId(), true);
			// Attach the contact ID to the group
			BdfDictionary meta = new BdfDictionary();
			meta.put(CONTACT_ID, c.getId().getInt());
			meta.put(TO_BE_SHARED_BY_US, new BdfList());
			meta.put(SHARED_BY_US, new BdfList());
			meta.put(SHARED_WITH_US, new BdfList());
			clientHelper.mergeGroupMetadata(txn, g.getId(), meta);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		// query for this contact c
		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(CONTACT_ID, c.getId().getInt())
		);

		// clean up session states with that contact from localGroup
		try {
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, localGroup.getId(),
							query);
			for (Map.Entry<MessageId, BdfDictionary> entry : map.entrySet()) {
				deleteMessage(txn, entry.getKey());
			}
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}

		// remove the contact group (all messages will be removed with it)
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	protected void incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary d) throws DbException, FormatException {

		BaseMessage msg = BaseMessage.from(m.getGroupId(), d);
		SessionId sessionId = msg.getSessionId();

		if (msg.getType() == SHARE_MSG_TYPE_INVITATION) {
			// we are an invitee who just received a new invitation
			boolean stateExists = true;
			try {
				// check if we have a session with that ID already
				getSessionState(txn, sessionId, false);
			} catch (FormatException e) {
				// this is what we would expect under normal circumstances
				stateExists = false;
			}
			try {
				// check if we already have a state with that sessionId
				if (stateExists) throw new FormatException();

				// check if forum can be shared
				Invitation invitation = (Invitation) msg;
				Forum f = forumFactory.createForum(invitation.getForumName(),
						invitation.getForumSalt());
				ContactId contactId = getContactId(txn, m.getGroupId());
				Contact contact = db.getContact(txn, contactId);
				if (!canBeShared(txn, f.getId(), contact))
					throw new FormatException();

				// initialize state and process invitation
				InviteeSessionState state =
						initializeInviteeState(txn, contactId, invitation);
				InviteeEngine engine = new InviteeEngine(forumFactory);
				processInviteeStateUpdate(txn, m.getId(),
						engine.onMessageReceived(state, msg));
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				deleteMessage(txn, m.getId());
			}
		} else if (msg.getType() == SHARE_MSG_TYPE_ACCEPT ||
				msg.getType() == SHARE_MSG_TYPE_DECLINE) {
			// we are a sharer who just received a response
			SharerSessionState state = getSessionStateForSharer(txn, sessionId);
			SharerEngine engine = new SharerEngine();
			processSharerStateUpdate(txn, m.getId(),
					engine.onMessageReceived(state, msg));
		} else if (msg.getType() == SHARE_MSG_TYPE_LEAVE ||
				msg.getType() == SHARE_MSG_TYPE_ABORT) {
			// we don't know who we are, so figure it out
			ForumSharingSessionState s = getSessionState(txn, sessionId, true);
			if (s instanceof SharerSessionState) {
				// we are a sharer and the invitee wants to leave or abort
				SharerSessionState state = (SharerSessionState) s;
				SharerEngine engine = new SharerEngine();
				processSharerStateUpdate(txn, m.getId(),
						engine.onMessageReceived(state, msg));
			} else {
				// we are an invitee and the sharer wants to leave or abort
				InviteeSessionState state = (InviteeSessionState) s;
				InviteeEngine engine = new InviteeEngine(forumFactory);
				processInviteeStateUpdate(txn, m.getId(),
						engine.onMessageReceived(state, msg));
			}
		} else {
			// message has passed validator, so that should never happen
			throw new RuntimeException("Illegal Forum Sharing Message");
		}
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void sendForumInvitation(GroupId groupId, ContactId contactId,
			String msg) throws DbException {

		Transaction txn = db.startTransaction(false);
		try {
			// initialize local state for sharer
			Forum f = forumManager.getForum(txn, groupId);
			SharerSessionState localState =
					initializeSharerState(txn, f, contactId);

			// add invitation message to local state to be available for engine
			if (!StringUtils.isNullOrEmpty(msg)) {
				localState.setMessage(msg);
			}

			// start engine and process its state update
			SharerEngine engine = new SharerEngine();
			processSharerStateUpdate(txn, null,
					engine.onLocalAction(localState, Action.LOCAL_INVITATION));

			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void respondToInvitation(Forum f, Contact c, boolean accept)
			throws DbException {

		Transaction txn = db.startTransaction(false);
		try {
			// find session state based on forum
			InviteeSessionState localState =
					getSessionStateForResponse(txn, f, c);

			// define action
			InviteeSessionState.Action localAction;
			if (accept) {
				localAction = InviteeSessionState.Action.LOCAL_ACCEPT;
			} else {
				localAction = InviteeSessionState.Action.LOCAL_DECLINE;
			}

			// start engine and process its state update
			InviteeEngine engine = new InviteeEngine(forumFactory);
			processInviteeStateUpdate(txn, null,
					engine.onLocalAction(localState, localAction));

			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Collection<ForumInvitationMessage> getForumInvitationMessages(
			ContactId contactId) throws DbException {

		// query for all invitations
		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(TYPE, SHARE_MSG_TYPE_INVITATION)
		);

		Transaction txn = db.startTransaction(false);
		try {
			Contact contact = db.getContact(txn, contactId);
			Group group = getContactGroup(contact);

			Collection<ForumInvitationMessage> list =
					new ArrayList<ForumInvitationMessage>();
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, group.getId(), query);
			for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
				BdfDictionary d = m.getValue();
				try {
					Invitation msg = Invitation.from(group.getId(), d);
					MessageStatus status =
							db.getMessageStatus(txn, contactId, m.getKey());
					long time = d.getLong(TIME);
					boolean local = d.getBoolean(LOCAL);
					boolean read = d.getBoolean(READ, false);
					boolean available = false;
					if (!local) {
						// figure out whether the forum is still available
						ForumSharingSessionState s =
								getSessionState(txn, msg.getSessionId(), true);
						if (!(s instanceof InviteeSessionState))
							continue;
						available = ((InviteeSessionState) s).getState() ==
								InviteeSessionState.State.AWAIT_LOCAL_RESPONSE;
					}
					ForumInvitationMessage im =
							new ForumInvitationMessage(m.getKey(),
									msg.getSessionId(), contactId,
									msg.getForumName(), msg.getMessage(),
									available, time, local, status.isSent(),
									status.isSeen(), read);
					list.add(im);
				} catch (FormatException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
			txn.setComplete();
			return list;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void removingForum(Transaction txn, Forum f) throws DbException {
		try {
			for (Contact c : db.getContacts(txn)) {
				GroupId g = getContactGroup(c).getId();
				if (removeFromList(txn, g, TO_BE_SHARED_BY_US, f)) {
					leaveForum(txn, c.getId(), f);
				}
				if (removeFromList(txn, g, SHARED_BY_US, f)) {
					leaveForum(txn, c.getId(), f);
				}
				if (removeFromList(txn, g, SHARED_WITH_US, f)) {
					leaveForum(txn, c.getId(), f);
				}
			}
		} catch (IOException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Forum> getAvailableForums() throws DbException {
		try {
			Set<Forum> available = new HashSet<Forum>();
			Transaction txn = db.startTransaction(true);
			try {
				// Get any forums we subscribe to
				Set<Group> subscribed = new HashSet<Group>(db.getGroups(txn,
						forumManager.getClientId()));
				// Get all forums shared by contacts
				for (Contact c : db.getContacts(txn)) {
					Group g = getContactGroup(c);
					List<Forum> forums =
							getForumList(txn, g.getId(), SHARED_WITH_US);
					for (Forum f : forums) {
						if (!subscribed.contains(f.getGroup()))
							available.add(f);
					}
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return Collections.unmodifiableSet(available);
		} catch (IOException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Contact> getSharedBy(GroupId g) throws DbException {
		try {
			List<Contact> subscribers = new ArrayList<Contact>();
			Transaction txn = db.startTransaction(true);
			try {
				for (Contact c : db.getContacts(txn)) {
					GroupId contactGroup = getContactGroup(c).getId();
					if (listContains(txn, contactGroup, g, SHARED_WITH_US))
						subscribers.add(c);
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return Collections.unmodifiableList(subscribers);
		} catch (IOException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<ContactId> getSharedWith(GroupId g) throws DbException {
		try {
			List<ContactId> shared = new ArrayList<ContactId>();
			Transaction txn = db.startTransaction(true);
			try {
				for (Contact c : db.getContacts(txn)) {
					GroupId contactGroup = getContactGroup(c).getId();
					if (listContains(txn, contactGroup, g, SHARED_BY_US))
						shared.add(c.getId());
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return Collections.unmodifiableList(shared);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public boolean canBeShared(GroupId g, Contact c) throws DbException {
		boolean canBeShared;
		Transaction txn = db.startTransaction(true);
		try {
			canBeShared = canBeShared(txn, g, c);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return canBeShared;
	}

	private boolean canBeShared(Transaction txn, GroupId g, Contact c)
			throws DbException {

		try {
			GroupId contactGroup = getContactGroup(c).getId();
			return !listContains(txn, contactGroup, g, SHARED_BY_US) &&
					!listContains(txn, contactGroup, g, SHARED_WITH_US) &&
					!listContains(txn, contactGroup, g, TO_BE_SHARED_BY_US);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private SharerSessionState initializeSharerState(Transaction txn, Forum f,
			ContactId contactId) throws FormatException, DbException {

		Contact c = db.getContact(txn, contactId);
		Group group = getContactGroup(c);

		// create local message to keep engine state
		long now = clock.currentTimeMillis();
		Bytes salt = new Bytes(new byte[FORUM_SALT_LENGTH]);
		random.nextBytes(salt.getBytes());
		Message m = clientHelper.createMessage(localGroup.getId(), now,
				BdfList.of(salt));
		SessionId sessionId = new SessionId(m.getId().getBytes());

		SharerSessionState s = new SharerSessionState(sessionId, sessionId,
				group.getId(), SharerSessionState.State.PREPARE_INVITATION,
				contactId, f.getId(), f.getName(), f.getSalt());

		// save local state to database
		BdfDictionary d = s.toBdfDictionary();
		clientHelper.addLocalMessage(txn, m, getClientId(), d, false);

		return s;
	}

	private InviteeSessionState initializeInviteeState(Transaction txn,
			ContactId contactId, Invitation msg)
			throws FormatException, DbException {

		Contact c = db.getContact(txn, contactId);
		Group group = getContactGroup(c);
		String name = msg.getForumName();
		byte[] salt = msg.getForumSalt();
		Forum f = forumFactory.createForum(name, salt);

		// create local message to keep engine state
		long now = clock.currentTimeMillis();
		Bytes mSalt = new Bytes(new byte[FORUM_SALT_LENGTH]);
		random.nextBytes(mSalt.getBytes());
		Message m = clientHelper.createMessage(localGroup.getId(), now,
				BdfList.of(mSalt));

		InviteeSessionState s = new InviteeSessionState(msg.getSessionId(),
				m.getId(), group.getId(),
				InviteeSessionState.State.AWAIT_INVITATION, contactId,
				f.getId(), f.getName(), f.getSalt());

		// save local state to database
		BdfDictionary d = s.toBdfDictionary();
		clientHelper.addLocalMessage(txn, m, getClientId(), d, false);

		return s;
	}

	private ForumSharingSessionState getSessionState(Transaction txn,
			SessionId sessionId, boolean warn)
			throws DbException, FormatException {

		try {
			return getSessionStateForSharer(txn, sessionId);
		} catch (NoSuchMessageException e) {
			// State not found directly, so query for state for invitee
			BdfDictionary query = BdfDictionary.of(
					new BdfEntry(SESSION_ID, sessionId)
			);

			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, localGroup.getId(),
							query);

			if (map.size() > 1 && LOG.isLoggable(WARNING)) {
				LOG.warning(
						"More than one session state found for message with session ID " +
								Arrays.hashCode(sessionId.getBytes()));
			}
			if (map.isEmpty()) {
				if (warn && LOG.isLoggable(WARNING)) {
					LOG.warning(
							"No session state found for message with session ID " +
									Arrays.hashCode(sessionId.getBytes()));
				}
				throw new FormatException();
			}
			return fromBdfDictionary(map.values().iterator().next());
		}
	}

	private SharerSessionState getSessionStateForSharer(Transaction txn,
			SessionId sessionId)
			throws DbException, FormatException {

		// we should be able to get the sharer state directly from sessionId
		BdfDictionary d =
				clientHelper.getMessageMetadataAsDictionary(txn, sessionId);

		if (!d.getBoolean(IS_SHARER)) throw new FormatException();

		return (SharerSessionState) fromBdfDictionary(d);
	}

	private InviteeSessionState getSessionStateForResponse(Transaction txn,
			Forum f, Contact c) throws DbException, FormatException {

		// query for invitee states for that forum in state await response
		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(IS_SHARER, false),
				new BdfEntry(CONTACT_ID, c.getId().getInt()),
				new BdfEntry(FORUM_ID, f.getId()),
				new BdfEntry(STATE,
						InviteeSessionState.State.AWAIT_LOCAL_RESPONSE
								.getValue())
		);

		Map<MessageId, BdfDictionary> map = clientHelper
				.getMessageMetadataAsDictionary(txn, localGroup.getId(), query);

		if (map.size() > 1 && LOG.isLoggable(WARNING)) {
			LOG.warning(
					"More than one session state found for forum with ID " +
							Arrays.hashCode(f.getId().getBytes()) +
							" in state AWAIT_LOCAL_RESPONSE for contact " +
							c.getAuthor().getName());
		}
		if (map.isEmpty()) {
			if (LOG.isLoggable(WARNING)) {
				LOG.warning(
						"No session state found for forum with ID " +
								Arrays.hashCode(f.getId().getBytes()) +
								" in state AWAIT_LOCAL_RESPONSE");
			}
			throw new DbException();
		}
		return (InviteeSessionState) fromBdfDictionary(
				map.values().iterator().next());
	}

	private ForumSharingSessionState getSessionStateForLeaving(Transaction txn,
			Forum f, ContactId c) throws DbException, FormatException {

		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(CONTACT_ID, c.getInt()),
				new BdfEntry(FORUM_ID, f.getId())
		);
		Map<MessageId, BdfDictionary> map = clientHelper
				.getMessageMetadataAsDictionary(txn, localGroup.getId(), query);
		for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
			BdfDictionary d = m.getValue();
			try {
				ForumSharingSessionState s = fromBdfDictionary(d);

				// check that a forum get be left in current session
				if (s instanceof SharerSessionState) {
					SharerSessionState state = (SharerSessionState) s;
					SharerSessionState.State nextState =
							state.getState().next(Action.LOCAL_LEAVE);
					if (nextState != SharerSessionState.State.ERROR) {
						return state;
					}
				} else {
					InviteeSessionState state = (InviteeSessionState) s;
					InviteeSessionState.State nextState = state.getState()
							.next(InviteeSessionState.Action.LOCAL_LEAVE);
					if (nextState != InviteeSessionState.State.ERROR) {
						return state;
					}
				}
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		throw new FormatException();
	}

	private void processStateUpdate(Transaction txn, MessageId messageId,
			StateUpdate<ForumSharingSessionState, BaseMessage> result)
			throws DbException, FormatException {

		// perform actions based on new local state
		performTasks(txn, result.localState);

		// save new local state
		MessageId storageId = result.localState.getStorageId();
		clientHelper.mergeMessageMetadata(txn, storageId,
				result.localState.toBdfDictionary());

		// send messages
		for (BaseMessage msg : result.toSend) {
			sendMessage(txn, msg);
		}

		// broadcast events
		for (Event event : result.toBroadcast) {
			txn.attach(event);
		}

		// delete message
		if (result.deleteMessage && messageId != null) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Deleting message with id " + messageId.hashCode());
			}
			db.deleteMessage(txn, messageId);
			db.deleteMessageMetadata(txn, messageId);
		}
	}

	private void processSharerStateUpdate(Transaction txn, MessageId messageId,
			StateUpdate<SharerSessionState, BaseMessage> result)
			throws DbException, FormatException {

		StateUpdate<ForumSharingSessionState, BaseMessage> r =
				new StateUpdate<ForumSharingSessionState, BaseMessage>(
						result.deleteMessage, result.deleteState,
						result.localState, result.toSend, result.toBroadcast);

		processStateUpdate(txn, messageId, r);
	}

	private void processInviteeStateUpdate(Transaction txn, MessageId messageId,
			StateUpdate<InviteeSessionState, BaseMessage> result)
			throws DbException, FormatException {

		StateUpdate<ForumSharingSessionState, BaseMessage> r =
				new StateUpdate<ForumSharingSessionState, BaseMessage>(
						result.deleteMessage, result.deleteState,
						result.localState, result.toSend, result.toBroadcast);

		processStateUpdate(txn, messageId, r);
	}

	private void performTasks(Transaction txn, ForumSharingSessionState localState)
			throws FormatException, DbException {

		if (localState.getTask() == -1) return;

		// remember task and remove it from localState
		long task = localState.getTask();
		localState.setTask(-1);

		// get group ID for later
		GroupId groupId = localState.getGroupId();
		// get contact ID for later
		ContactId contactId = localState.getContactId();

		// get forum for later
		String name = localState.getForumName();
		byte[] salt = localState.getForumSalt();
		Forum f = forumFactory.createForum(name, salt);

		// perform tasks
		if (task == TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US) {
			addToList(txn, groupId, SHARED_WITH_US, f);
		}
		else if (task == TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US) {
			removeFromList(txn, groupId, SHARED_WITH_US, f);
		}
		else if (task == TASK_ADD_SHARED_FORUM) {
			db.addGroup(txn, f.getGroup());
			db.setVisibleToContact(txn, contactId, f.getId(), true);
		}
		else if (task == TASK_ADD_FORUM_TO_LIST_TO_BE_SHARED_BY_US) {
			addToList(txn, groupId, TO_BE_SHARED_BY_US, f);
		}
		else if (task == TASK_REMOVE_FORUM_FROM_LIST_TO_BE_SHARED_BY_US) {
			removeFromList(txn, groupId, TO_BE_SHARED_BY_US, f);
		}
		else if (task == TASK_SHARE_FORUM) {
			db.setVisibleToContact(txn, contactId, f.getId(), true);
			removeFromList(txn, groupId, TO_BE_SHARED_BY_US, f);
			addToList(txn, groupId, SHARED_BY_US, f);
		}
		else if (task == TASK_UNSHARE_FORUM_SHARED_BY_US) {
			db.setVisibleToContact(txn, contactId, f.getId(), false);
			removeFromList(txn, groupId, SHARED_BY_US, f);
		} else if (task == TASK_UNSHARE_FORUM_SHARED_WITH_US) {
			db.setVisibleToContact(txn, contactId, f.getId(), false);
			removeFromList(txn, groupId, SHARED_WITH_US, f);
		}
	}

	private void sendMessage(Transaction txn, BaseMessage m)
			throws FormatException, DbException {

		byte[] body = clientHelper.toByteArray(m.toBdfList());
		Group group = db.getGroup(txn, m.getGroupId());
		long timestamp = clock.currentTimeMillis();

		// add message itself as metadata
		BdfDictionary d = m.toBdfDictionary();
		d.put(LOCAL, true);
		d.put(TIME, timestamp);
		Metadata meta = metadataEncoder.encode(d);

		messageQueueManager
				.sendMessage(txn, group, timestamp, body, meta);
	}

	private Group getContactGroup(Contact c) {
		return privateGroupFactory.createPrivateGroup(CLIENT_ID, c);
	}

	private ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException, FormatException {
		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn,
				contactGroupId);
		return new ContactId(meta.getLong(CONTACT_ID).intValue());
	}

	private void leaveForum(Transaction txn, ContactId c, Forum f)
			throws DbException, FormatException {

		ForumSharingSessionState state = getSessionStateForLeaving(txn, f, c);
		if (state instanceof SharerSessionState) {
			Action action = Action.LOCAL_LEAVE;
			SharerEngine engine = new SharerEngine();
			processSharerStateUpdate(txn, null,
					engine.onLocalAction((SharerSessionState) state, action));
		} else {
			InviteeSessionState.Action action =
					InviteeSessionState.Action.LOCAL_LEAVE;
			InviteeEngine engine = new InviteeEngine(forumFactory);
			processInviteeStateUpdate(txn, null,
					engine.onLocalAction((InviteeSessionState) state, action));
		}
	}

	private boolean listContains(Transaction txn, GroupId contactGroup,
			GroupId forum, String key) throws DbException, FormatException {

		List<Forum> list = getForumList(txn, contactGroup, key);
		for (Forum f : list) {
			if (f.getId().equals(forum)) return true;
		}
		return false;
	}

	private boolean addToList(Transaction txn, GroupId groupId, String key,
			Forum f) throws DbException, FormatException {

		List<Forum> forums = getForumList(txn, groupId, key);
		if (forums.contains(f)) return false;
		forums.add(f);
		storeForumList(txn, groupId, key, forums);
		return true;
	}

	private boolean removeFromList(Transaction txn, GroupId groupId, String key,
			Forum f) throws DbException, FormatException {

		List<Forum> forums = getForumList(txn, groupId, key);
		if (forums.remove(f)) {
			storeForumList(txn, groupId, key, forums);
			return true;
		}
		return false;
	}

	private List<Forum> getForumList(Transaction txn, GroupId groupId,
			String key) throws DbException, FormatException {

		BdfDictionary metadata =
				clientHelper.getGroupMetadataAsDictionary(txn, groupId);
		BdfList list = metadata.getList(key);

		return parseForumList(list);
	}

	private void storeForumList(Transaction txn, GroupId groupId, String key,
			List<Forum> forums)	throws DbException, FormatException {

		BdfList list = encodeForumList(forums);
		BdfDictionary metadata = BdfDictionary.of(
				new BdfEntry(key, list)
		);
		clientHelper.mergeGroupMetadata(txn, groupId, metadata);
	}

	private BdfList encodeForumList(List<Forum> forums) {
		BdfList forumList = new BdfList();
		for (Forum f : forums)
			forumList.add(BdfList.of(f.getName(), f.getSalt()));
		return forumList;
	}

	private List<Forum> parseForumList(BdfList list) throws FormatException {
		List<Forum> forums = new ArrayList<Forum>(list.size());
		for (int i = 0; i < list.size(); i++) {
			BdfList forum = list.getList(i);
			forums.add(forumFactory
					.createForum(forum.getString(0), forum.getRaw(1)));
		}
		return forums;
	}

	private void deleteMessage(Transaction txn, MessageId messageId)
			throws DbException {

		if (LOG.isLoggable(INFO))
			LOG.info("Deleting message with ID: " + messageId.hashCode());

		db.deleteMessage(txn, messageId);
		db.deleteMessageMetadata(txn, messageId);
	}

}
