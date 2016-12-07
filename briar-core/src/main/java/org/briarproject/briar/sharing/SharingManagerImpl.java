package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.AddContactHook;
import org.briarproject.bramble.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationMessage;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.SharingInvitationItem;
import org.briarproject.briar.api.sharing.SharingManager;
import org.briarproject.briar.api.sharing.event.InvitationRequestReceivedEvent;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;
import org.briarproject.briar.client.ConversationClientImpl;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.briar.api.client.ProtocolEngine.StateUpdate;
import static org.briarproject.briar.api.sharing.SharingConstants.CONTACT_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.IS_SHARER;
import static org.briarproject.briar.api.sharing.SharingConstants.LOCAL;
import static org.briarproject.briar.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.briar.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.SHAREABLE_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARED_BY_US;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARED_WITH_US;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARING_SALT_LENGTH;
import static org.briarproject.briar.api.sharing.SharingConstants.STATE;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_ADD_SHAREABLE_TO_LIST_SHARED_WITH_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_ADD_SHAREABLE_TO_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_ADD_SHARED_SHAREABLE;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_REMOVE_SHAREABLE_FROM_LIST_SHARED_WITH_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_REMOVE_SHAREABLE_FROM_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_SHARE_SHAREABLE;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_UNSHARE_SHAREABLE_SHARED_BY_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_UNSHARE_SHAREABLE_SHARED_WITH_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TIME;
import static org.briarproject.briar.api.sharing.SharingConstants.TO_BE_SHARED_BY_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TYPE;
import static org.briarproject.briar.api.sharing.SharingMessage.BaseMessage;
import static org.briarproject.briar.api.sharing.SharingMessage.Invitation;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
import static org.briarproject.briar.sharing.InviteeSessionState.State.AWAIT_LOCAL_RESPONSE;

@NotNullByDefault
abstract class SharingManagerImpl<S extends Shareable, I extends Invitation, IS extends InviteeSessionState, SS extends SharerSessionState, IR extends InvitationRequestReceivedEvent, IRR extends InvitationResponseReceivedEvent>
		extends ConversationClientImpl
		implements SharingManager<S>, Client, AddContactHook,
		RemoveContactHook {

	private static final Logger LOG =
			Logger.getLogger(SharingManagerImpl.class.getName());

	private final MessageQueueManager messageQueueManager;
	private final MetadataEncoder metadataEncoder;
	private final SecureRandom random;
	private final ContactGroupFactory contactGroupFactory;
	private final Clock clock;
	private final Group localGroup;

	SharingManagerImpl(DatabaseComponent db,
			MessageQueueManager messageQueueManager, ClientHelper clientHelper,
			MetadataParser metadataParser, MetadataEncoder metadataEncoder,
			SecureRandom random, ContactGroupFactory contactGroupFactory,
			MessageTracker messageTracker, Clock clock) {
		super(db, clientHelper, metadataParser, messageTracker);
		this.messageQueueManager = messageQueueManager;
		this.metadataEncoder = metadataEncoder;
		this.random = random;
		this.contactGroupFactory = contactGroupFactory;
		this.clock = clock;
		localGroup = contactGroupFactory.createLocalGroup(getClientId());
	}

	protected abstract ClientId getClientId();

	protected abstract InvitationMessage createInvitationRequest(MessageId id,
			I msg, ContactId contactId, boolean available, long time,
			boolean local, boolean sent, boolean seen, boolean read);

	protected abstract InvitationMessage createInvitationResponse(MessageId id,
			SessionId sessionId, GroupId groupId, ContactId contactId,
			boolean accept, long time, boolean local, boolean sent,
			boolean seen, boolean read);

	protected abstract ShareableFactory<S, I, IS, SS> getSFactory();

	protected abstract InvitationFactory<I, SS> getIFactory();

	protected abstract InviteeSessionStateFactory<S, IS> getISFactory();

	protected abstract SharerSessionStateFactory<S, SS> getSSFactory();

	protected abstract InvitationReceivedEventFactory<IS, IR> getIRFactory();

	protected abstract InvitationResponseReceivedEventFactory<SS, IRR> getIRRFactory();

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
			db.setGroupVisibility(txn, c.getId(), g.getId(), SHARED);
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
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary d) throws DbException, FormatException {

		BaseMessage msg = BaseMessage.from(getIFactory(), m.getGroupId(), d);
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
			// check if we already have a state with that sessionId
			if (stateExists) throw new FormatException();

			// check if shareable can be shared
			I invitation = (I) msg;
			S f = getSFactory().parse(invitation);
			ContactId contactId = getContactId(txn, m.getGroupId());
			Contact contact = db.getContact(txn, contactId);
			if (!canBeShared(txn, f.getId(), contact))
				checkForRaceCondition(txn, f, contact);

			// initialize state and process invitation
			IS state = initializeInviteeState(txn, contactId, invitation,
					m.getId());
			InviteeEngine<IS, IR> engine =
					new InviteeEngine<IS, IR>(getIRFactory(), clock);
			processInviteeStateUpdate(txn, m.getId(),
					engine.onMessageReceived(state, msg));
			messageTracker.trackIncomingMessage(txn, m);
		} else if (msg.getType() == SHARE_MSG_TYPE_ACCEPT ||
				msg.getType() == SHARE_MSG_TYPE_DECLINE) {
			// we are a sharer who just received a response
			SS state = getSessionStateForSharer(txn, sessionId);
			state.setResponseId(m.getId());
			SharerEngine<I, SS, IRR> engine =
					new SharerEngine<I, SS, IRR>(getIFactory(),
							getIRRFactory(), clock);
			processSharerStateUpdate(txn, m.getId(),
					engine.onMessageReceived(state, msg));
			messageTracker.trackIncomingMessage(txn, m);
		} else if (msg.getType() == SHARE_MSG_TYPE_LEAVE ||
				msg.getType() == SHARE_MSG_TYPE_ABORT) {
			// we don't know who we are, so figure it out
			SharingSessionState s = getSessionState(txn, sessionId, true);
			if (s instanceof SharerSessionState) {
				// we are a sharer and the invitee wants to leave or abort
				SS state = (SS) s;
				SharerEngine<I, SS, IRR> engine =
						new SharerEngine<I, SS, IRR>(getIFactory(),
								getIRRFactory(), clock);
				processSharerStateUpdate(txn, m.getId(),
						engine.onMessageReceived(state, msg));
			} else {
				// we are an invitee and the sharer wants to leave or abort
				IS state = (IS) s;
				InviteeEngine<IS, IR> engine =
						new InviteeEngine<IS, IR>(getIRFactory(), clock);
				processInviteeStateUpdate(txn, m.getId(),
						engine.onMessageReceived(state, msg));
			}
		} else {
			// message has passed validator, so that should never happen
			throw new AssertionError("Illegal Sharing Message");
		}
		// don't share message as other party already has it
		return false;
	}

	@Override
	public void sendInvitation(GroupId groupId, ContactId contactId,
			@Nullable String msg) throws DbException {

		Transaction txn = db.startTransaction(false);
		try {
			// initialize local state for sharer
			S f = getSFactory().get(txn, groupId);
			SS localState = initializeSharerState(txn, f, contactId);

			// add invitation message to local state to be available for engine
			if (!StringUtils.isNullOrEmpty(msg)) {
				int msgLength = StringUtils.toUtf8(msg).length;
				if (msgLength > MAX_INVITATION_MESSAGE_LENGTH)
					throw new IllegalArgumentException();
				localState.setMessage(msg);
			}

			// start engine and process its state update
			SharerEngine<I, SS, IRR> engine =
					new SharerEngine<I, SS, IRR>(getIFactory(),
							getIRRFactory(), clock);
			StateUpdate<SS, BaseMessage> update =
					engine.onLocalAction(localState,
							SharerSessionState.Action.LOCAL_INVITATION);
			processSharerStateUpdate(txn, null, update);

			// track message
			// TODO handle this properly without engine hacks (#376)
			long time = update.toSend.get(0).getTime();
			messageTracker.trackMessage(txn, localState.getGroupId(), time,
					true);

			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void respondToInvitation(S f, Contact c, boolean accept)
			throws DbException {

		Transaction txn = db.startTransaction(false);
		try {
			// find session state based on shareable
			IS localState = getSessionStateForResponse(txn, f, c);
			respondToInvitation(txn, localState, accept);
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void respondToInvitation(SessionId id, boolean accept)
			throws DbException {

		Transaction txn = db.startTransaction(false);
		try {
			IS localState = (IS) getSessionState(txn, id, true);
			respondToInvitation(txn, localState, accept);
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private void respondToInvitation(Transaction txn, IS localState,
			boolean accept) throws DbException, FormatException {
		// define action
		InviteeSessionState.Action localAction;
		if (accept) {
			localAction = InviteeSessionState.Action.LOCAL_ACCEPT;
		} else {
			localAction = InviteeSessionState.Action.LOCAL_DECLINE;
		}

		// start engine and process its state update
		InviteeEngine<IS, IR> engine =
				new InviteeEngine<IS, IR>(getIRFactory(), clock);
		StateUpdate<IS, BaseMessage> update =
				engine.onLocalAction(localState, localAction);
		processInviteeStateUpdate(txn, null, update);

		// track message
		// TODO handle this properly without engine hacks (#376)
		long time = update.toSend.get(0).getTime();
		messageTracker.trackMessage(txn, localState.getGroupId(), time, true);
	}

	@Override
	public Collection<InvitationMessage> getInvitationMessages(
			ContactId contactId)
			throws DbException {

		Transaction txn = db.startTransaction(true);
		try {
			Contact contact = db.getContact(txn, contactId);
			Group group = getContactGroup(contact);

			Collection<InvitationMessage> list =
					new ArrayList<InvitationMessage>();
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, group.getId());
			for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
				BdfDictionary d = m.getValue();
				long type = d.getLong(TYPE);
				if (type == SHARE_MSG_TYPE_LEAVE ||
						type == SHARE_MSG_TYPE_ABORT) continue;
				try {
					MessageStatus status =
							db.getMessageStatus(txn, contactId, m.getKey());
					long time = d.getLong(TIME);
					boolean local = d.getBoolean(LOCAL);
					boolean read = d.getBoolean(MSG_KEY_READ, false);
					boolean available = false;

					if (type == SHARE_MSG_TYPE_INVITATION) {
						I msg = getIFactory().build(group.getId(), d);
						if (!local) {
							// figure out whether the shareable is still available
							SharingSessionState s =
									getSessionState(txn, msg.getSessionId(),
											true);
							if (!(s instanceof InviteeSessionState))
								continue;
							available = ((InviteeSessionState) s).getState() ==
									AWAIT_LOCAL_RESPONSE;
						}
						InvitationMessage im =
								createInvitationRequest(m.getKey(), msg,
										contactId, available, time, local,
										status.isSent(), status.isSeen(), read);
						list.add(im);
					} else if (type == SHARE_MSG_TYPE_ACCEPT ||
							type == SHARE_MSG_TYPE_DECLINE) {
						boolean accept = type == SHARE_MSG_TYPE_ACCEPT;
						BaseMessage msg = BaseMessage
								.from(getIFactory(), group.getId(), d);
						SessionId sessionId = msg.getSessionId();
						InvitationMessage im = createInvitationResponse(
								m.getKey(), sessionId, group.getId(), contactId,
								accept, time, local, status.isSent(),
								status.isSeen(), read);
						list.add(im);
					} else {
						throw new RuntimeException("Unexpected Message Type");
					}
				} catch (FormatException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
			db.commitTransaction(txn);
			return list;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Collection<SharingInvitationItem> getInvitations()
			throws DbException {
		List<SharingInvitationItem> invitations =
				new ArrayList<SharingInvitationItem>();
		Transaction txn = db.startTransaction(true);
		try {
			Set<S> shareables = new HashSet<S>();
			Map<GroupId, Collection<Contact>> newSharers =
					new HashMap<GroupId, Collection<Contact>>();
			Collection<Contact> contacts = db.getContacts(txn);

			// get invitations from each contact
			for (Contact contact : contacts) {
				Collection<S> newShareables = getInvited(txn, contact);
				shareables.addAll(newShareables);
				for (S s : newShareables) {
					if (newSharers.containsKey(s.getId())) {
						newSharers.get(s.getId()).add(contact);
					} else {
						Collection<Contact> c = new ArrayList<Contact>();
						c.add(contact);
						newSharers.put(s.getId(), c);
					}
				}
			}
			// construct InvitationItem objects
			for (S s : shareables) {
				Collection<Contact> newS = newSharers.get(s.getId());
				boolean subscribed = db.containsGroup(txn, s.getId());
				SharingInvitationItem invitation =
						new SharingInvitationItem(s, subscribed, newS);
				invitations.add(invitation);
			}
			db.commitTransaction(txn);
			return invitations;
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	private Collection<S> getInvited(Transaction txn, Contact contact)
			throws DbException, FormatException {

		// query for all external invitations
		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(TYPE, SHARE_MSG_TYPE_INVITATION),
				new BdfEntry(LOCAL, false)
		);
		Group group = getContactGroup(contact);

		Set<S> invited = new HashSet<S>();
		Map<MessageId, BdfDictionary> map = clientHelper
				.getMessageMetadataAsDictionary(txn, group.getId(), query);
		for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
			BdfDictionary d = m.getValue();
			try {
				I msg = getIFactory().build(group.getId(), d);
				IS iss = (IS) getSessionState(txn, msg.getSessionId(), true);
				// get and add the shareable if the invitation is unanswered
				if (iss.getState().equals(AWAIT_LOCAL_RESPONSE)) {
					S s = getSFactory().parse(iss);
					invited.add(s);
				}
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e.toString(), e);
			}
		}
		return invited;
	}

	@Override
	public Collection<Contact> getSharedWith(GroupId g) throws DbException {
		try {
			List<Contact> shared = new ArrayList<Contact>();
			Transaction txn = db.startTransaction(true);
			try {
				for (Contact c : db.getContacts(txn)) {
					GroupId contactGroup = getContactGroup(c).getId();
					if (listContains(txn, contactGroup, g, SHARED_BY_US))
						shared.add(c);
					else if (listContains(txn, contactGroup, g, SHARED_WITH_US))
						shared.add(c);
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
			return shared;
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
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return canBeShared;
	}

	protected boolean canBeShared(Transaction txn, GroupId g, Contact c)
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

	void removingShareable(Transaction txn, S f) throws DbException {
		try {
			for (Contact c : db.getContacts(txn)) {
				GroupId g = getContactGroup(c).getId();
				if (removeFromList(txn, g, TO_BE_SHARED_BY_US, f)) {
					leaveShareable(txn, c.getId(), f);
				}
				if (removeFromList(txn, g, SHARED_BY_US, f)) {
					leaveShareable(txn, c.getId(), f);
				}
				if (removeFromList(txn, g, SHARED_WITH_US, f)) {
					leaveShareable(txn, c.getId(), f);
				}
			}
		} catch (IOException e) {
			throw new DbException(e);
		}
	}

	private void checkForRaceCondition(Transaction txn, S f, Contact c)
			throws FormatException, DbException {

		GroupId contactGroup = getContactGroup(c).getId();
		if (!listContains(txn, contactGroup, f.getId(), TO_BE_SHARED_BY_US))
			// no race-condition, this invitation is invalid
			throw new FormatException();

		// we have an invitation race condition
		LocalAuthor author = db.getLocalAuthor(txn, c.getLocalAuthorId());
		Bytes ourKey = new Bytes(author.getPublicKey());
		Bytes theirKey = new Bytes(c.getAuthor().getPublicKey());

		// determine which invitation takes precedence
		boolean alice = ourKey.compareTo(theirKey) < 0;

		if (alice) {
			// our own invitation takes precedence, so just delete Bob's
			LOG.info(
					"Invitation race-condition: We are Alice deleting Bob's invitation.");
			throw new FormatException();
		} else {
			// we are Bob, so we need to "take back" our own invitation
			LOG.info(
					"Invitation race-condition: We are Bob taking back our invitation.");
			SharingSessionState state =
					getSessionStateForLeaving(txn, f, c.getId());
			if (state instanceof SharerSessionState) {
				//SharerEngine engine = new SharerEngine();
				//processSharerStateUpdate(txn, null,
				//		engine.onLocalAction((SharerSessionState) state,
				//				Action.LOCAL_LEAVE));

				// simply remove from list instead of involving engine
				removeFromList(txn, contactGroup, TO_BE_SHARED_BY_US, f);
				// TODO here we could also remove the old session state
				//      and invitation message
			}
		}

	}

	private SS initializeSharerState(Transaction txn, S f,
			ContactId contactId) throws FormatException, DbException {

		Contact c = db.getContact(txn, contactId);
		Group group = getContactGroup(c);

		// create local message to keep engine state
		long now = clock.currentTimeMillis();
		Bytes salt = new Bytes(new byte[SHARING_SALT_LENGTH]);
		random.nextBytes(salt.getBytes());
		Message m = clientHelper.createMessage(localGroup.getId(), now,
				BdfList.of(salt));
		SessionId sessionId = new SessionId(m.getId().getBytes());

		SS s = getSSFactory().build(sessionId, m.getId(), group.getId(),
				SharerSessionState.State.PREPARE_INVITATION, contactId, f);

		// save local state to database
		BdfDictionary d = s.toBdfDictionary();
		clientHelper.addLocalMessage(txn, m, d, false);

		return s;
	}

	private IS initializeInviteeState(Transaction txn,
			ContactId contactId, I msg, MessageId id)
			throws FormatException, DbException {

		Contact c = db.getContact(txn, contactId);
		Group group = getContactGroup(c);
		S f = getSFactory().parse(msg);

		// create local message to keep engine state
		long now = clock.currentTimeMillis();
		Bytes mSalt = new Bytes(new byte[SHARING_SALT_LENGTH]);
		random.nextBytes(mSalt.getBytes());
		Message m = clientHelper.createMessage(localGroup.getId(), now,
				BdfList.of(mSalt));

		IS s = getISFactory()
				.build(msg.getSessionId(), m.getId(), group.getId(),
						InviteeSessionState.State.AWAIT_INVITATION, contactId,
						f, id);

		// save local state to database
		BdfDictionary d = s.toBdfDictionary();
		clientHelper.addLocalMessage(txn, m, d, false);

		return s;
	}

	private SharingSessionState getSessionState(Transaction txn,
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
			return SharingSessionState
					.fromBdfDictionary(getISFactory(), getSSFactory(),
							map.values().iterator().next());
		}
	}

	private SS getSessionStateForSharer(Transaction txn,
			SessionId sessionId)
			throws DbException, FormatException {

		// we should be able to get the sharer state directly from sessionId
		MessageId storageId = new MessageId(sessionId.getBytes());
		BdfDictionary d =
				clientHelper.getMessageMetadataAsDictionary(txn, storageId);

		if (!d.getBoolean(IS_SHARER)) throw new FormatException();

		return (SS) SharingSessionState
				.fromBdfDictionary(getISFactory(), getSSFactory(), d);
	}

	private IS getSessionStateForResponse(Transaction txn,
			S f, Contact c) throws DbException, FormatException {

		// query for invitee states for that shareable in state await response
		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(IS_SHARER, false),
				new BdfEntry(CONTACT_ID, c.getId().getInt()),
				new BdfEntry(SHAREABLE_ID, f.getId()),
				new BdfEntry(STATE, AWAIT_LOCAL_RESPONSE.getValue())
		);

		Map<MessageId, BdfDictionary> map = clientHelper
				.getMessageMetadataAsDictionary(txn, localGroup.getId(), query);

		if (map.size() > 1 && LOG.isLoggable(WARNING)) {
			LOG.warning(
					"More than one session state found for shareable with ID " +
							Arrays.hashCode(f.getId().getBytes()) +
							" in state AWAIT_LOCAL_RESPONSE for contact " +
							c.getAuthor().getName());
		}
		if (map.isEmpty()) {
			if (LOG.isLoggable(WARNING)) {
				LOG.warning(
						"No session state found for shareable with ID " +
								Arrays.hashCode(f.getId().getBytes()) +
								" in state AWAIT_LOCAL_RESPONSE");
			}
			throw new DbException();
		}
		return (IS) SharingSessionState
				.fromBdfDictionary(getISFactory(), getSSFactory(),
						map.values().iterator().next());
	}

	private SharingSessionState getSessionStateForLeaving(Transaction txn,
			S f, ContactId c) throws DbException, FormatException {

		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(CONTACT_ID, c.getInt()),
				new BdfEntry(SHAREABLE_ID, f.getId())
		);
		Map<MessageId, BdfDictionary> map = clientHelper
				.getMessageMetadataAsDictionary(txn, localGroup.getId(), query);
		for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
			BdfDictionary d = m.getValue();
			try {
				SharingSessionState s = SharingSessionState
						.fromBdfDictionary(getISFactory(), getSSFactory(), d);

				// check that a shareable get be left in current session
				if (s instanceof SharerSessionState) {
					SharerSessionState state = (SharerSessionState) s;
					SharerSessionState.State nextState =
							state.getState()
									.next(SharerSessionState.Action.LOCAL_LEAVE);
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

	private void processStateUpdate(Transaction txn,
			@Nullable MessageId messageId,
			StateUpdate<SharingSessionState, BaseMessage> result, S f)
			throws DbException, FormatException {

		// perform actions based on new local state
		performTasks(txn, result.localState, f);

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

	private void processSharerStateUpdate(Transaction txn,
			@Nullable MessageId messageId, StateUpdate<SS, BaseMessage> result)
			throws DbException, FormatException {

		StateUpdate<SharingSessionState, BaseMessage> r =
				new StateUpdate<SharingSessionState, BaseMessage>(
						result.deleteMessage, result.deleteState,
						result.localState, result.toSend, result.toBroadcast);

		// get shareable for later
		S f = getSFactory().parse(result.localState);

		processStateUpdate(txn, messageId, r, f);
	}

	private void processInviteeStateUpdate(Transaction txn,
			@Nullable MessageId messageId, StateUpdate<IS, BaseMessage> result)
			throws DbException, FormatException {

		StateUpdate<SharingSessionState, BaseMessage> r =
				new StateUpdate<SharingSessionState, BaseMessage>(
						result.deleteMessage, result.deleteState,
						result.localState, result.toSend, result.toBroadcast);

		// get shareable for later
		S f = getSFactory().parse(result.localState);

		processStateUpdate(txn, messageId, r, f);
	}

	private void performTasks(Transaction txn, SharingSessionState localState,
			S f) throws FormatException, DbException {

		if (localState.getTask() == -1) return;

		// remember task and remove it from localState
		long task = localState.getTask();
		localState.setTask(-1);

		// get group ID for later
		GroupId groupId = localState.getGroupId();
		// get contact ID for later
		ContactId contactId = localState.getContactId();

		// perform tasks
		if (task == TASK_ADD_SHAREABLE_TO_LIST_SHARED_WITH_US) {
			addToList(txn, groupId, SHARED_WITH_US, f);
		} else if (task == TASK_REMOVE_SHAREABLE_FROM_LIST_SHARED_WITH_US) {
			removeFromList(txn, groupId, SHARED_WITH_US, f);
		} else if (task == TASK_ADD_SHARED_SHAREABLE) {
			// TODO we might want to call the add() method of the respective
			//      manager here, because blogs add a description for example
			db.addGroup(txn, f.getGroup());
			db.setGroupVisibility(txn, contactId, f.getId(), SHARED);
		} else if (task == TASK_ADD_SHAREABLE_TO_LIST_TO_BE_SHARED_BY_US) {
			addToList(txn, groupId, TO_BE_SHARED_BY_US, f);
		} else if (task == TASK_REMOVE_SHAREABLE_FROM_LIST_TO_BE_SHARED_BY_US) {
			removeFromList(txn, groupId, TO_BE_SHARED_BY_US, f);
		} else if (task == TASK_SHARE_SHAREABLE) {
			db.setGroupVisibility(txn, contactId, f.getId(), SHARED);
			removeFromList(txn, groupId, TO_BE_SHARED_BY_US, f);
			addToList(txn, groupId, SHARED_BY_US, f);
		} else if (task == TASK_UNSHARE_SHAREABLE_SHARED_BY_US) {
			db.setGroupVisibility(txn, contactId, f.getId(), INVISIBLE);
			removeFromList(txn, groupId, SHARED_BY_US, f);
		} else if (task == TASK_UNSHARE_SHAREABLE_SHARED_WITH_US) {
			db.setGroupVisibility(txn, contactId, f.getId(), INVISIBLE);
			removeFromList(txn, groupId, SHARED_WITH_US, f);
		}
	}

	private void sendMessage(Transaction txn, BaseMessage m)
			throws FormatException, DbException {

		byte[] body = clientHelper.toByteArray(m.toBdfList());
		Group group = db.getGroup(txn, m.getGroupId());

		// add message itself as metadata
		BdfDictionary d = m.toBdfDictionary();
		d.put(LOCAL, true);
		d.put(TIME, m.getTime());
		Metadata meta = metadataEncoder.encode(d);

		messageQueueManager
				.sendMessage(txn, group, m.getTime(), body, meta);
	}

	@Override
	public Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(getClientId(), c);
	}

	private ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException, FormatException {
		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn,
				contactGroupId);
		return new ContactId(meta.getLong(CONTACT_ID).intValue());
	}

	private void leaveShareable(Transaction txn, ContactId c, S f)
			throws DbException, FormatException {

		SharingSessionState state = getSessionStateForLeaving(txn, f, c);
		if (state instanceof SharerSessionState) {
			SharerSessionState.Action action =
					SharerSessionState.Action.LOCAL_LEAVE;
			SharerEngine<I, SS, IRR> engine =
					new SharerEngine<I, SS, IRR>(getIFactory(),
							getIRRFactory(), clock);
			processSharerStateUpdate(txn, null,
					engine.onLocalAction((SS) state, action));
		} else {
			InviteeSessionState.Action action =
					InviteeSessionState.Action.LOCAL_LEAVE;
			InviteeEngine<IS, IR> engine =
					new InviteeEngine<IS, IR>(getIRFactory(), clock);
			processInviteeStateUpdate(txn, null,
					engine.onLocalAction((IS) state, action));
		}
	}

	private boolean listContains(Transaction txn, GroupId contactGroup,
			GroupId shareable, String key) throws DbException, FormatException {

		List<S> list = getShareableList(txn, contactGroup, key);
		for (S f : list) {
			if (f.getId().equals(shareable)) return true;
		}
		return false;
	}

	private boolean addToList(Transaction txn, GroupId groupId, String key,
			S f) throws DbException, FormatException {

		List<S> shareables = getShareableList(txn, groupId, key);
		if (shareables.contains(f)) return false;
		shareables.add(f);
		storeShareableList(txn, groupId, key, shareables);
		return true;
	}

	private boolean removeFromList(Transaction txn, GroupId groupId, String key,
			S f) throws DbException, FormatException {

		List<S> shareables = getShareableList(txn, groupId, key);
		if (shareables.remove(f)) {
			storeShareableList(txn, groupId, key, shareables);
			return true;
		}
		return false;
	}

	private List<S> getShareableList(Transaction txn, GroupId groupId,
			String key) throws DbException, FormatException {

		BdfDictionary metadata =
				clientHelper.getGroupMetadataAsDictionary(txn, groupId);
		BdfList list = metadata.getList(key);

		return parseShareableList(list);
	}

	private void storeShareableList(Transaction txn, GroupId groupId,
			String key,
			List<S> shareables) throws DbException, FormatException {

		BdfList list = encodeShareableList(shareables);
		BdfDictionary metadata = BdfDictionary.of(
				new BdfEntry(key, list)
		);
		clientHelper.mergeGroupMetadata(txn, groupId, metadata);
	}

	private BdfList encodeShareableList(List<S> shareables) {
		BdfList shareableList = new BdfList();
		for (S f : shareables)
			shareableList.add(getSFactory().encode(f));
		return shareableList;
	}

	private List<S> parseShareableList(BdfList list) throws FormatException {
		List<S> shareables = new ArrayList<S>(list.size());
		for (int i = 0; i < list.size(); i++) {
			BdfList shareable = list.getList(i);
			shareables.add(getSFactory().parse(shareable));
		}
		return shareables;
	}

	private void deleteMessage(Transaction txn, MessageId messageId)
			throws DbException {

		if (LOG.isLoggable(INFO))
			LOG.info("Deleting message with ID: " + messageId.hashCode());

		db.deleteMessage(txn, messageId);
		db.deleteMessageMetadata(txn, messageId);
	}

}
