package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.Client;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager.AddContactHook;
import org.briarproject.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.introduction.IntroducerProtocolState;
import org.briarproject.introduction.IntroducerSessionState;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.introduction.IntroductionMessage;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.introduction.IntroduceeProtocolState.FINISHED;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.api.introduction.IntroductionConstants.READ;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;

class IntroductionManagerImpl extends BdfIncomingMessageHook
		implements IntroductionManager, Client, AddContactHook,
		RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"23b1897c198a90ae75b976ac023d0f32"
					+ "80ca67b12f2346b2c23a34f34e2434c3"));

	private static final Logger LOG =
			Logger.getLogger(IntroductionManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final IntroducerManager introducerManager;
	private final IntroduceeManager introduceeManager;
	private final IntroductionGroupFactory introductionGroupFactory;

	@Inject
	IntroductionManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			MetadataParser metadataParser, IntroducerManager introducerManager,
			IntroduceeManager introduceeManager,
			IntroductionGroupFactory introductionGroupFactory) {

		super(clientHelper, metadataParser);
		this.db = db;
		this.introducerManager = introducerManager;
		this.introduceeManager = introduceeManager;
		this.introductionGroupFactory = introductionGroupFactory;
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		db.addGroup(txn, introductionGroupFactory.createLocalGroup());
		// Ensure we've set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		try {
			// Create an introduction group for sending introduction messages
			Group g = introductionGroupFactory.createIntroductionGroup(c);
			// Return if we've already set things up for this contact
			if (db.containsGroup(txn, g.getId())) return;
			// Store the group and share it with the contact
			db.addGroup(txn, g);
			db.setVisibleToContact(txn, c.getId(), g.getId(), true);
			// Attach the contact ID to the group
			BdfDictionary gm = new BdfDictionary();
			gm.put(CONTACT, c.getId().getInt());
			clientHelper.mergeGroupMetadata(txn, g.getId(), gm);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		GroupId gId = introductionGroupFactory.createLocalGroup().getId();

		// search for session states where c introduced us
		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(ROLE, ROLE_INTRODUCEE),
				new BdfEntry(CONTACT_ID_1, c.getId().getInt())
		);
		try {
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, gId, query);
			for (Map.Entry<MessageId, BdfDictionary> entry : map.entrySet()) {
				// delete states if introducee removes introducer
				deleteMessage(txn, entry.getKey());
			}
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}

		// check for open sessions with c and abort those,
		// so the other introducee knows
		query = BdfDictionary.of(
				new BdfEntry(ROLE, ROLE_INTRODUCER)
		);
		try {
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, gId, query);
			for (Map.Entry<MessageId, BdfDictionary> entry : map.entrySet()) {
				BdfDictionary d = entry.getValue();
				IntroducerSessionState stateobj =
						IntroducerSessionState.fromBdfDictionary(d);
				ContactId c1 = stateobj.getContact1Id();
				ContactId c2 = stateobj.getContact2Id();

				if (c1.equals(c.getId()) || c2.equals(c.getId())) {
					// abort protocol if still ongoing
					if (IntroducerProtocolState.isOngoing(stateobj.getState()))
						introducerManager.abort(txn, stateobj);
					// also delete state if both contacts have been deleted
					if (c1.equals(c.getId())) {
						try {
							db.getContact(txn, c2);
						} catch (NoSuchContactException e) {
							deleteMessage(txn, entry.getKey());
						}
					} else if (c2.equals(c.getId())) {
						try {
							db.getContact(txn, c1);
						} catch (NoSuchContactException e) {
							deleteMessage(txn, entry.getKey());
						}
					}
				}
			}
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}

		// remove the group (all messages will be removed with it)
		// this contact won't get our abort message, but the other will
		db.removeGroup(txn, introductionGroupFactory.createIntroductionGroup(c));
	}

	/**
	 * This is called when a new message arrived and is being validated.
	 * It is the central method where we determine which role we play
	 * in the introduction protocol and which engine we need to start.
	 */
	@Override
	protected void incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary message) throws DbException, FormatException{

		// Get message data and type
		GroupId groupId = m.getGroupId();
		long type = message.getLong(TYPE, -1L);

		// we are an introducee, need to initialize new state
		if (type == TYPE_REQUEST) {
			boolean stateExists = true;
			SessionId sessionId;
			IntroduceeSessionState state;
				sessionId = new SessionId(message.getRaw(SESSION_ID));

			try {
				getSessionState(txn, groupId, sessionId.getBytes(), false);
			} catch (FormatException e) {
				stateExists = false;
			}
			try {
				if (stateExists) throw new FormatException();
				state = introduceeManager
						.initialize(txn, sessionId, groupId, message);
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning(
							"Could not initialize introducee state, deleting...");
					LOG.log(WARNING, e.toString(), e);
				}
				deleteMessage(txn, m.getId());
				return;
			}
			try {
				introduceeManager.incomingMessage(txn, state, message);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				introduceeManager.abort(txn, state);
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				introduceeManager.abort(txn, state);
			}
		}
		// our role can be anything
		else if (type == TYPE_RESPONSE || type == TYPE_ACK || type == TYPE_ABORT) {
			IntroductionState state;
			try {
				state = getSessionState(txn, groupId, message.getRaw(SESSION_ID));
			} catch (FormatException e) {
				LOG.warning("Could not find state for message, deleting...");
				deleteMessage(txn, m.getId());
				return;
			}

			try {
				if (state instanceof IntroducerSessionState) {
					introducerManager.incomingMessage(txn, 
							(IntroducerSessionState)state, message);
				} else if (state instanceof IntroduceeSessionState) {
					introduceeManager.incomingMessage(txn, 
							(IntroduceeSessionState) state, message);
				} else {
					if(LOG.isLoggable(WARNING)) {
						LOG.warning("Unknown role '" 
								+ state.getClass().getName()
								+ "'. Deleting message...");
						deleteMessage(txn, m.getId());
					}
					throw new RuntimeException("Unknown role" +
												state.getClass().getName());
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				if (state instanceof IntroducerSessionState)
					introducerManager.abort(txn, 
							(IntroducerSessionState)state);
				else introduceeManager.abort(txn, 
							(IntroduceeSessionState) state);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				if (state instanceof IntroducerSessionState)
					introducerManager.abort(txn, (IntroducerSessionState)state);
				else introduceeManager.abort(txn, 
						(IntroduceeSessionState) state);
			}
		} else {
			// the message has been validated, so this should not happen
			if(LOG.isLoggable(WARNING)) {
				LOG.warning("Unknown message type '" + type + "', deleting...");
			}
		}
	}

	@Override
	public void makeIntroduction(Contact c1, Contact c2, String msg,
			final long timestamp)
			throws DbException, FormatException {

		Transaction txn = db.startTransaction(false);
		try {
			introducerManager.makeIntroduction(txn, c1, c2, msg, timestamp);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void acceptIntroduction(final ContactId contactId,
			final SessionId sessionId, final long timestamp)
			throws DbException, FormatException {

		Transaction txn = db.startTransaction(false);
		try {
			Contact c = db.getContact(txn, contactId);
			Group g = introductionGroupFactory.createIntroductionGroup(c);
			IntroduceeSessionState state =
					(IntroduceeSessionState) getSessionState(txn, g.getId(),
							sessionId.getBytes());

			introduceeManager.acceptIntroduction(txn, state, timestamp);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void declineIntroduction(final ContactId contactId,
			final SessionId sessionId, final long timestamp)
			throws DbException, FormatException {

		Transaction txn = db.startTransaction(false);
		try {
			Contact c = db.getContact(txn, contactId);
			Group g = introductionGroupFactory.createIntroductionGroup(c);
			IntroductionState state =
					getSessionState(txn, g.getId(), sessionId.getBytes());

			introduceeManager.declineIntroduction(txn, 
					(IntroduceeSessionState) state, timestamp);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Collection<IntroductionMessage> getIntroductionMessages(
			ContactId contactId) throws DbException {

		Collection<IntroductionMessage> list =
				new ArrayList<IntroductionMessage>();

		Map<MessageId, BdfDictionary> metadata;
		Collection<MessageStatus> statuses;
		Transaction txn = db.startTransaction(true);
		try {
			// get messages and their status
			GroupId g = introductionGroupFactory
					.createIntroductionGroup(db.getContact(txn, contactId))
					.getId();
			metadata = clientHelper.getMessageMetadataAsDictionary(txn, g);
			statuses = db.getMessageStatus(txn, contactId, g);

			// turn messages into classes for the UI
			for (MessageStatus s : statuses) {
				MessageId messageId = s.getMessageId();
				BdfDictionary msg = metadata.get(messageId);
				if (msg == null) continue;

				try {
					long type = msg.getLong(TYPE);
					if (type == TYPE_ACK || type == TYPE_ABORT) continue;

					// get session state
					SessionId sessionId = new SessionId(msg.getRaw(SESSION_ID));
					IntroductionState state =
							getSessionState(txn, g, sessionId.getBytes());

					boolean local;
					long time = msg.getLong(MESSAGE_TIME);
					boolean accepted = msg.getBoolean(ACCEPT, false);
					boolean read = msg.getBoolean(READ, false);
					AuthorId authorId;
					String name;

					int role;
					if (state instanceof IntroducerSessionState) {
						role = ROLE_INTRODUCER;
					} else {
						role = ROLE_INTRODUCEE;
					}
					if (type == TYPE_RESPONSE) {
						if (role == ROLE_INTRODUCER) {
							IntroducerSessionState iss =
								(IntroducerSessionState)state;
							if (!concernsThisContact(contactId, messageId,
										iss)) {
								// this response is not from contactId
								continue;
							}
							local = false;
							authorId = getAuthorIdForIntroducer(contactId, iss);
							name = getNameForIntroducer(contactId, iss);
						} else {
							IntroduceeSessionState iss = 
								(IntroduceeSessionState) state;
							if (Arrays.equals(iss.getOtherResponseId(),
											  messageId.getBytes())) {
								// this response is not ours,
								// check if it was a decline
								if (!accepted) {
									local = false;
								} else {
									// don't include positive responses
									continue;
								}
							} else {
								local = true;
							}

							authorId = iss.getRemoteAuthorId();
							name = iss.getIntroducedName();
						}
						IntroductionResponse ir = new IntroductionResponse(
								sessionId, messageId, role, time, local,
								s.isSent(), s.isSeen(), read, authorId, name,
								accepted);
						list.add(ir);
					} else if (type == TYPE_REQUEST) {
						String message;
						boolean answered, exists, introducesOtherIdentity;
						if (role == ROLE_INTRODUCER) {
							IntroducerSessionState iss =
								(IntroducerSessionState)state;
							local = true;
							authorId = getAuthorIdForIntroducer(contactId, iss);
							name = getNameForIntroducer(contactId, iss);
							message = msg.getOptionalString(MSG);
							answered = false;
							exists = false;
							introducesOtherIdentity = false;
						} else {
							IntroduceeSessionState iss =
								(IntroduceeSessionState) state;
							local = false;
							authorId = iss.getRemoteAuthorId();
							name = iss.getIntroducedName();
							message = iss.getMessage();
							boolean finished = iss.getState() == FINISHED;
							answered = finished || iss.getAnswered();
							exists = iss.getContactExists();
							introducesOtherIdentity = iss.getRemoteAuthorIsUs();
						}
						IntroductionRequest ir = new IntroductionRequest(
								sessionId, messageId, role, time, local,
								s.isSent(), s.isSeen(), read, authorId, name,
								accepted, message, answered, exists,
								introducesOtherIdentity);
						list.add(ir);
					}
				} catch (FormatException e) {
					if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				}
			}
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return list;
	}

	private String getNameForIntroducer(ContactId contactId,
			IntroducerSessionState state) throws FormatException {

		if (contactId.equals(state.getContact2Id()))
			return state.getContact1Name();
		if (contactId.equals(state.getContact1Id()))
			return state.getContact2Name();
		throw new RuntimeException("Contact not part of this introduction session");
	}

	private AuthorId getAuthorIdForIntroducer(ContactId contactId,
			IntroducerSessionState state) throws FormatException {

		if (contactId.equals(state.getContact1Id()))
			return state.getContact2AuthorId();
		if (contactId.equals(state.getContact2Id()))
			return state.getContact1AuthorId();
		throw new RuntimeException("Contact not part of this introduction session");
	}

	private boolean concernsThisContact(ContactId contactId, MessageId messageId,
			IntroducerSessionState state) throws FormatException {

		if (contactId.equals(state.getContact1Id())) {
			return state.getResponse1().equals(messageId);
		} else {
			return state.getResponse2().equals(messageId);
		}
	}

	private IntroductionState getSessionState(Transaction txn, GroupId groupId,
			byte[] sessionId, boolean warn)
			throws DbException, FormatException {

		try {
			// See if we can find the state directly for the introducer
			BdfDictionary state = clientHelper
					.getMessageMetadataAsDictionary(txn,
							new MessageId(sessionId));
			GroupId g1 = new GroupId(state.getRaw(GROUP_ID_1));
			GroupId g2 = new GroupId(state.getRaw(GROUP_ID_2));
			if (!g1.equals(groupId) && !g2.equals(groupId)) {
				throw new NoSuchMessageException();
			}
			return IntroductionState.fromBdfDictionary(state);
		} catch (NoSuchMessageException e) {
			// State not found directly, so iterate over all states
			// to find state for introducee
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn,
							introductionGroupFactory.createLocalGroup().getId());
			for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
				if (Arrays.equals(m.getValue().getRaw(SESSION_ID), sessionId)) {
					BdfDictionary state = m.getValue();
					GroupId g = new GroupId(state.getRaw(GROUP_ID));
					if (g.equals(groupId)) 
						return IntroductionState.fromBdfDictionary(state);
				}
			}
			if (warn && LOG.isLoggable(WARNING)) {
				LOG.warning(
						"No session state found for message with session ID " +
								Arrays.hashCode(sessionId));
			}
			throw new FormatException();
		}
	}

	private IntroductionState getSessionState(Transaction txn, GroupId groupId,
			byte[] sessionId) throws DbException, FormatException {

		return getSessionState(txn, groupId, sessionId, true);
	}

	private void deleteMessage(Transaction txn, MessageId messageId)
			throws DbException {

		db.deleteMessage(txn, messageId);
		db.deleteMessageMetadata(txn, messageId);
	}

}
