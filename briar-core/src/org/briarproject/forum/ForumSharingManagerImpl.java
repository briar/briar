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
import org.briarproject.api.forum.InviteeAction;
import org.briarproject.api.forum.InviteeProtocolState;
import org.briarproject.api.forum.SharerAction;
import org.briarproject.api.forum.SharerProtocolState;
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
import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.GROUP_ID;
import static org.briarproject.api.forum.ForumConstants.INVITATION_MSG;
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
import static org.briarproject.api.forum.ForumConstants.STORAGE_ID;
import static org.briarproject.api.forum.ForumConstants.TASK;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US;
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
import static org.briarproject.api.forum.InviteeProtocolState.AWAIT_INVITATION;
import static org.briarproject.api.forum.InviteeProtocolState.AWAIT_LOCAL_RESPONSE;
import static org.briarproject.api.forum.SharerProtocolState.PREPARE_INVITATION;

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
		// clean up session states with that contact from localGroup
		Long id = (long) c.getId().getInt();
		try {
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, localGroup.getId());
			for (Map.Entry<MessageId, BdfDictionary> entry : map.entrySet()) {
				BdfDictionary d = entry.getValue();
				if (id.equals(d.getLong(CONTACT_ID))) {
					deleteMessage(txn, entry.getKey());
				}
			}
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}

		// remove the contact group (all messages will be removed with it)
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	protected void incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary msg) throws DbException, FormatException {

		SessionId sessionId = new SessionId(msg.getRaw(SESSION_ID));
		long type = msg.getLong(TYPE);
		if (type == SHARE_MSG_TYPE_INVITATION) {
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
				Forum f = forumFactory.createForum(msg.getString(FORUM_NAME),
						msg.getRaw(FORUM_SALT));
				ContactId contactId = getContactId(txn, m.getGroupId());
				Contact contact = db.getContact(txn, contactId);
				if (!canBeShared(txn, f.getId(), contact))
					throw new FormatException();

				// initialize state and process invitation
				BdfDictionary state =
						initializeInviteeState(txn, contactId, msg);
				InviteeEngine engine = new InviteeEngine(forumFactory);
				processStateUpdate(txn, m.getId(),
						engine.onMessageReceived(state, msg));
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				deleteMessage(txn, m.getId());
			}
		} else if (type == SHARE_MSG_TYPE_ACCEPT ||
				type == SHARE_MSG_TYPE_DECLINE) {
			// we are a sharer who just received a response
			BdfDictionary state = getSessionState(txn, sessionId, true);
			SharerEngine engine = new SharerEngine();
			processStateUpdate(txn, m.getId(),
					engine.onMessageReceived(state, msg));
		} else if (type == SHARE_MSG_TYPE_LEAVE ||
				type == SHARE_MSG_TYPE_ABORT) {
			// we don't know who we are, so figure it out
			BdfDictionary state = getSessionState(txn, sessionId, true);
			if (state.getBoolean(IS_SHARER)) {
				// we are a sharer and the invitee wants to leave or abort
				SharerEngine engine = new SharerEngine();
				processStateUpdate(txn, m.getId(),
						engine.onMessageReceived(state, msg));
			} else {
				// we are an invitee and the sharer wants to leave or abort
				InviteeEngine engine = new InviteeEngine(forumFactory);
				processStateUpdate(txn, m.getId(),
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
			BdfDictionary localState = initializeSharerState(txn, f, contactId);

			// define action
			BdfDictionary localAction = new BdfDictionary();
			localAction.put(TYPE, SHARE_MSG_TYPE_INVITATION);
			if (!StringUtils.isNullOrEmpty(msg)) {
				localAction.put(INVITATION_MSG, msg);
			}

			// start engine and process its state update
			SharerEngine engine = new SharerEngine();
			processStateUpdate(txn, null,
					engine.onLocalAction(localState, localAction));

			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void respondToInvitation(Forum f, boolean accept)
			throws DbException {

		Transaction txn = db.startTransaction(false);
		try {
			// find session state based on forum
			BdfDictionary localState = getSessionStateForResponse(txn, f);

			// define action
			BdfDictionary localAction = new BdfDictionary();
			if (accept) {
				localAction.put(TYPE, SHARE_MSG_TYPE_ACCEPT);
			} else {
				localAction.put(TYPE, SHARE_MSG_TYPE_DECLINE);
			}

			// start engine and process its state update
			InviteeEngine engine = new InviteeEngine(forumFactory);
			processStateUpdate(txn, null,
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

		Transaction txn = db.startTransaction(false);
		try {
			Contact contact = db.getContact(txn, contactId);
			Group group = getContactGroup(contact);

			Collection<ForumInvitationMessage> list =
					new ArrayList<ForumInvitationMessage>();
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, group.getId());
			for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
				BdfDictionary msg = m.getValue();
				try {
					if (msg.getLong(TYPE) != SHARE_MSG_TYPE_INVITATION)
						continue;

					MessageStatus status =
							db.getMessageStatus(txn, contactId, m.getKey());
					SessionId sessionId = new SessionId(msg.getRaw(SESSION_ID));
					String name = msg.getString(FORUM_NAME);
					String message = msg.getOptionalString(INVITATION_MSG);
					long time = msg.getLong(TIME);
					boolean local = msg.getBoolean(LOCAL);
					boolean read = msg.getBoolean(READ, false);
					boolean available = false;
					if (!local) {
						// figure out whether the forum is still available
						BdfDictionary sessionState =
								getSessionState(txn, sessionId, true);
						InviteeProtocolState state = InviteeProtocolState
								.fromValue(
										sessionState.getLong(STATE).intValue());
						available = state == AWAIT_LOCAL_RESPONSE;
					}
					ForumInvitationMessage im =
							new ForumInvitationMessage(m.getKey(), sessionId,
									contactId, name, message, available, time,
									local, status.isSent(), status.isSeen(),
									read);
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

	private BdfDictionary initializeSharerState(Transaction txn, Forum f,
			ContactId contactId) throws FormatException, DbException {

		Contact c = db.getContact(txn, contactId);
		Group group = getContactGroup(c);

		// create local message to keep engine state
		long now = clock.currentTimeMillis();
		Bytes salt = new Bytes(new byte[FORUM_SALT_LENGTH]);
		random.nextBytes(salt.getBytes());
		Message m = clientHelper.createMessage(localGroup.getId(), now,
				BdfList.of(salt));
		MessageId sessionId = m.getId();

		BdfDictionary d = new BdfDictionary();
		d.put(SESSION_ID, sessionId);
		d.put(STORAGE_ID, sessionId);
		d.put(GROUP_ID, group.getId());
		d.put(IS_SHARER, true);
		d.put(STATE, PREPARE_INVITATION.getValue());
		d.put(CONTACT_ID, contactId.getInt());
		d.put(FORUM_ID, f.getId());
		d.put(FORUM_NAME, f.getName());
		d.put(FORUM_SALT, f.getSalt());

		// save local state to database
		clientHelper.addLocalMessage(txn, m, getClientId(), d, false);

		return d;
	}

	private BdfDictionary initializeInviteeState(Transaction txn,
			ContactId contactId, BdfDictionary msg)
			throws FormatException, DbException {

		Contact c = db.getContact(txn, contactId);
		Group group = getContactGroup(c);
		String name = msg.getString(FORUM_NAME);
		byte[] salt = msg.getRaw(FORUM_SALT);
		Forum f = forumFactory.createForum(name, salt);

		// create local message to keep engine state
		long now = clock.currentTimeMillis();
		Bytes mSalt = new Bytes(new byte[FORUM_SALT_LENGTH]);
		random.nextBytes(mSalt.getBytes());
		Message m = clientHelper.createMessage(localGroup.getId(), now,
				BdfList.of(mSalt));

		BdfDictionary d = new BdfDictionary();
		d.put(SESSION_ID, msg.getRaw(SESSION_ID));
		d.put(STORAGE_ID, m.getId());
		d.put(GROUP_ID, group.getId());
		d.put(IS_SHARER, false);
		d.put(STATE, AWAIT_INVITATION.getValue());
		d.put(CONTACT_ID, contactId.getInt());
		d.put(FORUM_ID, f.getId());
		d.put(FORUM_NAME, name);
		d.put(FORUM_SALT, salt);

		// save local state to database
		clientHelper.addLocalMessage(txn, m, getClientId(), d, false);

		return d;
	}

	private BdfDictionary getSessionState(Transaction txn, SessionId sessionId,
			boolean warn) throws DbException, FormatException {

		try {
			// we should be able to get the sharer state directly from sessionId
			return clientHelper.getMessageMetadataAsDictionary(txn, sessionId);
		} catch (NoSuchMessageException e) {
			// State not found directly, so iterate over all states
			// to find state for invitee
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, localGroup.getId());
			for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
				BdfDictionary state = m.getValue();
				if (Arrays.equals(state.getRaw(SESSION_ID),
						sessionId.getBytes())) {
					return state;
				}
			}
			if (warn && LOG.isLoggable(WARNING)) {
				LOG.warning(
						"No session state found for message with session ID " +
								Arrays.hashCode(sessionId.getBytes()));
			}
			throw new FormatException();
		}
	}

	private BdfDictionary getSessionStateForResponse(Transaction txn, Forum f)
			throws DbException, FormatException {

		Map<MessageId, BdfDictionary> map = clientHelper
				.getMessageMetadataAsDictionary(txn, localGroup.getId());
		for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
			BdfDictionary d = m.getValue();
			try {
				InviteeProtocolState state = InviteeProtocolState
						.fromValue(d.getLong(STATE).intValue());
				if (state == AWAIT_LOCAL_RESPONSE) {
					byte[] id = d.getRaw(FORUM_ID);
					if (Arrays.equals(f.getId().getBytes(), id)) {
						// Note that there should always be only one session
						// in this state for the same forum
						return d;
					}
				}
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		throw new DbException();
	}

	private BdfDictionary getSessionStateForLeaving(Transaction txn, Forum f,
			ContactId c) throws DbException, FormatException {

		Map<MessageId, BdfDictionary> map = clientHelper
				.getMessageMetadataAsDictionary(txn, localGroup.getId());
		for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
			BdfDictionary d = m.getValue();
			try {
				// check that this session is with the right contact
				if (c.getInt() != d.getLong(CONTACT_ID)) continue;
				// check that a forum get be left in current session
				int intState = d.getLong(STATE).intValue();
				if (d.getBoolean(IS_SHARER)) {
					SharerProtocolState state =
							SharerProtocolState.fromValue(intState);
					if (state.next(SharerAction.LOCAL_LEAVE) ==
							SharerProtocolState.ERROR) continue;
				} else {
					InviteeProtocolState state = InviteeProtocolState
							.fromValue(intState);
					if (state.next(InviteeAction.LOCAL_LEAVE) ==
							InviteeProtocolState.ERROR) continue;
				}
				// check that this state actually concerns this forum
				String name = d.getString(FORUM_NAME);
				byte[] salt = d.getRaw(FORUM_SALT);
				if (name.equals(f.getName()) &&
						Arrays.equals(salt, f.getSalt())) {
					// TODO what happens when there is more than one invitation?
					return d;
				}
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		throw new FormatException();
	}

	private void processStateUpdate(Transaction txn, MessageId messageId,
			StateUpdate<BdfDictionary, BdfDictionary> result)
			throws DbException, FormatException {

		// perform actions based on new local state
		performTasks(txn, result.localState);

		// save new local state
		MessageId storageId =
				new MessageId(result.localState.getRaw(STORAGE_ID));
		clientHelper.mergeMessageMetadata(txn, storageId, result.localState);

		// send messages
		for (BdfDictionary d : result.toSend) {
			sendMessage(txn, d);
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

	private void performTasks(Transaction txn, BdfDictionary localState)
			throws FormatException, DbException {

		if (!localState.containsKey(TASK)) return;

		// remember task and remove it from localState
		long task = localState.getLong(TASK);
		localState.put(TASK, BdfDictionary.NULL_VALUE);

		// get group ID for later
		GroupId groupId = new GroupId(localState.getRaw(GROUP_ID));
		// get contact ID for later
		ContactId contactId =
				new ContactId(localState.getLong(CONTACT_ID).intValue());

		// get forum for later
		String name = localState.getString(FORUM_NAME);
		byte[] salt = localState.getRaw(FORUM_SALT);
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

	private void sendMessage(Transaction txn, BdfDictionary m)
			throws FormatException, DbException {

		BdfList list = encodeMessage(m);
		byte[] body = clientHelper.toByteArray(list);
		GroupId groupId = new GroupId(m.getRaw(GROUP_ID));
		Group group = db.getGroup(txn, groupId);
		long timestamp = clock.currentTimeMillis();

		// add message itself as metadata
		m.put(LOCAL, true);
		m.put(TIME, timestamp);
		Metadata meta = metadataEncoder.encode(m);

		messageQueueManager
				.sendMessage(txn, group, timestamp, body, meta);
	}

	private BdfList encodeMessage(BdfDictionary m) throws FormatException {
		long type = m.getLong(TYPE);

		BdfList list;
		if (type == SHARE_MSG_TYPE_INVITATION) {
			list = BdfList.of(type,
					m.getRaw(SESSION_ID),
					m.getString(FORUM_NAME),
					m.getRaw(FORUM_SALT)
			);
			String msg = m.getOptionalString(INVITATION_MSG);
			if (msg != null) list.add(msg);
		} else if (type == SHARE_MSG_TYPE_ACCEPT) {
			list = BdfList.of(type, m.getRaw(SESSION_ID));
		} else if (type == SHARE_MSG_TYPE_DECLINE) {
			list = BdfList.of(type, m.getRaw(SESSION_ID));
		} else if (type == SHARE_MSG_TYPE_LEAVE) {
			list = BdfList.of(type, m.getRaw(SESSION_ID));
		} else if (type == SHARE_MSG_TYPE_ABORT) {
			list = BdfList.of(type, m.getRaw(SESSION_ID));
		} else {
			throw new FormatException();
		}
		return list;
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

		BdfDictionary state = getSessionStateForLeaving(txn, f, c);
		BdfDictionary action = new BdfDictionary();
		action.put(TYPE, SHARE_MSG_TYPE_LEAVE);
		if (state.getBoolean(IS_SHARER)) {
			SharerEngine engine = new SharerEngine();
			processStateUpdate(txn, null,
					engine.onLocalAction(state, action));
		} else {
			InviteeEngine engine = new InviteeEngine(forumFactory);
			processStateUpdate(txn, null,
					engine.onLocalAction(state, action));
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
