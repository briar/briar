package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.AddContactHook;
import org.briarproject.bramble.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.MessageStatus;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.IntroducerProtocolState;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.introduction.IntroductionMessage;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.client.ConversationClientImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.briar.api.introduction.IntroduceeProtocolState.FINISHED;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ANSWERED;
import static org.briarproject.briar.api.introduction.IntroductionConstants.AUTHOR_ID_1;
import static org.briarproject.briar.api.introduction.IntroductionConstants.AUTHOR_ID_2;
import static org.briarproject.briar.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.CONTACT_1;
import static org.briarproject.briar.api.introduction.IntroductionConstants.CONTACT_2;
import static org.briarproject.briar.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.briar.api.introduction.IntroductionConstants.CONTACT_ID_2;
import static org.briarproject.briar.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID_1;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID_2;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.REMOTE_AUTHOR_IS_US;
import static org.briarproject.briar.api.introduction.IntroductionConstants.RESPONSE_1;
import static org.briarproject.briar.api.introduction.IntroductionConstants.RESPONSE_2;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;

@Immutable
@NotNullByDefault
class IntroductionManagerImpl extends ConversationClientImpl
		implements IntroductionManager, Client, AddContactHook,
		RemoveContactHook {

	private static final Logger LOG =
			Logger.getLogger(IntroductionManagerImpl.class.getName());

	private final IntroducerManager introducerManager;
	private final IntroduceeManager introduceeManager;
	private final IntroductionGroupFactory introductionGroupFactory;

	@Inject
	IntroductionManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			MetadataParser metadataParser, MessageTracker messageTracker,
			IntroducerManager introducerManager,
			IntroduceeManager introduceeManager,
			IntroductionGroupFactory introductionGroupFactory) {

		super(db, clientHelper, metadataParser, messageTracker);
		this.introducerManager = introducerManager;
		this.introduceeManager = introduceeManager;
		this.introductionGroupFactory = introductionGroupFactory;
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
			Group g = getContactGroup(c);
			// Return if we've already set things up for this contact
			if (db.containsGroup(txn, g.getId())) return;
			// Store the group and share it with the contact
			db.addGroup(txn, g);
			db.setGroupVisibility(txn, c.getId(), g.getId(), SHARED);
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
				ContactId c1 = new ContactId(d.getLong(CONTACT_ID_1).intValue());
				ContactId c2 = new ContactId(d.getLong(CONTACT_ID_2).intValue());

				if (c1.equals(c.getId()) || c2.equals(c.getId())) {
					IntroducerProtocolState state = IntroducerProtocolState
							.fromValue(d.getLong(STATE).intValue());
					// abort protocol if still ongoing
					if (IntroducerProtocolState.isOngoing(state)) {
						introducerManager.abort(txn, d);
					}
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
		db.removeGroup(txn, getContactGroup(c));
	}

	/**
	 * This is called when a new message arrived and is being validated.
	 * It is the central method where we determine which role we play
	 * in the introduction protocol and which engine we need to start.
	 */
	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary message) throws DbException, FormatException {

		// Get message data and type
		GroupId groupId = m.getGroupId();
		long type = message.getLong(TYPE, -1L);

		// we are an introducee, need to initialize new state
		if (type == TYPE_REQUEST) {
			boolean stateExists = true;
			try {
				getSessionState(txn, groupId, message.getRaw(SESSION_ID), false);
			} catch (FormatException e) {
				stateExists = false;
			}
			if (stateExists) throw new FormatException();
			BdfDictionary state =
					introduceeManager.initialize(txn, groupId, message);
			try {
				introduceeManager.incomingMessage(txn, state, message);
				messageTracker.trackIncomingMessage(txn, m);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				introduceeManager.abort(txn, state);
			} catch (FormatException e) {
				// FIXME necessary?
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				introduceeManager.abort(txn, state);
			}
		}
		// our role can be anything
		else if (type == TYPE_RESPONSE || type == TYPE_ACK || type == TYPE_ABORT) {
			BdfDictionary state =
					getSessionState(txn, groupId, message.getRaw(SESSION_ID));

			long role = state.getLong(ROLE, -1L);
			try {
				if (role == ROLE_INTRODUCER) {
					introducerManager.incomingMessage(txn, state, message);
					if (type == TYPE_RESPONSE)
						messageTracker.trackIncomingMessage(txn, m);
				} else if (role == ROLE_INTRODUCEE) {
					introduceeManager.incomingMessage(txn, state, message);
					if (type == TYPE_RESPONSE && !message.getBoolean(ACCEPT))
						messageTracker.trackIncomingMessage(txn, m);
				} else {
					if (LOG.isLoggable(WARNING))
						LOG.warning("Unknown role '" + role + "'");
					throw new DbException();
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				if (role == ROLE_INTRODUCER) introducerManager.abort(txn, state);
				else introduceeManager.abort(txn, state);
			} catch (FormatException e) {
				// FIXME necessary?
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				if (role == ROLE_INTRODUCER) introducerManager.abort(txn, state);
				else introduceeManager.abort(txn, state);
			}
		} else {
			// the message has been validated, so this should not happen
			if(LOG.isLoggable(WARNING)) {
				LOG.warning("Unknown message type '" + type + "', deleting...");
			}
		}
		return false;
	}

	@Override
	public Group getContactGroup(Contact contact) {
		return introductionGroupFactory.createIntroductionGroup(contact);
	}

	@Override
	public void makeIntroduction(Contact c1, Contact c2, @Nullable String msg,
			final long timestamp) throws DbException, FormatException {

		Transaction txn = db.startTransaction(false);
		try {
			introducerManager.makeIntroduction(txn, c1, c2, msg, timestamp);
			Group g1 = getContactGroup(c1);
			Group g2 = getContactGroup(c2);
			messageTracker.trackMessage(txn, g1.getId(), timestamp, true);
			messageTracker.trackMessage(txn, g2.getId(), timestamp, true);
			db.commitTransaction(txn);
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
			Group g = getContactGroup(c);
			BdfDictionary state =
					getSessionState(txn, g.getId(), sessionId.getBytes());

			introduceeManager.acceptIntroduction(txn, state, timestamp);
			messageTracker.trackMessage(txn, g.getId(), timestamp, true);
			db.commitTransaction(txn);
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
			Group g = getContactGroup(c);
			BdfDictionary state =
					getSessionState(txn, g.getId(), sessionId.getBytes());

			introduceeManager.declineIntroduction(txn, state, timestamp);
			messageTracker.trackMessage(txn, g.getId(), timestamp, true);
			db.commitTransaction(txn);
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
			GroupId g = getContactGroup(db.getContact(txn, contactId)).getId();
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
					BdfDictionary state =
							getSessionState(txn, g, sessionId.getBytes());

					int role = state.getLong(ROLE).intValue();
					boolean local;
					long time = msg.getLong(MESSAGE_TIME);
					boolean accepted = msg.getBoolean(ACCEPT, false);
					boolean read = msg.getBoolean(MSG_KEY_READ, false);
					AuthorId authorId;
					String name;
					if (type == TYPE_RESPONSE) {
						if (role == ROLE_INTRODUCER) {
							if (!concernsThisContact(contactId, messageId, state)) {
								// this response is not from contactId
								continue;
							}
							local = false;
							authorId =
									getAuthorIdForIntroducer(contactId, state);
							name = getNameForIntroducer(contactId, state);
						} else {
							if (Arrays.equals(state.getRaw(NOT_OUR_RESPONSE),
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
							authorId = new AuthorId(
									state.getRaw(REMOTE_AUTHOR_ID));
							name = state.getString(NAME);
						}
						IntroductionResponse ir = new IntroductionResponse(
								sessionId, messageId, g, role, time, local,
								s.isSent(), s.isSeen(), read, authorId, name,
								accepted);
						list.add(ir);
					} else if (type == TYPE_REQUEST) {
						String message;
						boolean answered, exists, introducesOtherIdentity;
						if (role == ROLE_INTRODUCER) {
							local = true;
							authorId =
									getAuthorIdForIntroducer(contactId, state);
							name = getNameForIntroducer(contactId, state);
							message = msg.getOptionalString(MSG);
							answered = false;
							exists = false;
							introducesOtherIdentity = false;
						} else {
							local = false;
							authorId = new AuthorId(
									state.getRaw(REMOTE_AUTHOR_ID));
							name = state.getString(NAME);
							message = state.getOptionalString(MSG);
							boolean finished = state.getLong(STATE) ==
									FINISHED.getValue();
							answered = finished || state.getBoolean(ANSWERED);
							exists = state.getBoolean(EXISTS);
							introducesOtherIdentity =
									state.getBoolean(REMOTE_AUTHOR_IS_US);
						}
						IntroductionRequest ir = new IntroductionRequest(
								sessionId, messageId, g, role, time, local,
								s.isSent(), s.isSeen(), read, authorId, name,
								accepted, message, answered, exists,
								introducesOtherIdentity);
						list.add(ir);
					}
				} catch (FormatException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
			db.commitTransaction(txn);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		return list;
	}

	private String getNameForIntroducer(ContactId contactId,
			BdfDictionary state) throws FormatException {

		if (contactId.getInt() == state.getLong(CONTACT_ID_1).intValue())
			return state.getString(CONTACT_2);
		if (contactId.getInt() == state.getLong(CONTACT_ID_2).intValue())
			return state.getString(CONTACT_1);
		throw new RuntimeException(
				"Contact not part of this introduction session");
	}

	private AuthorId getAuthorIdForIntroducer(ContactId contactId,
			BdfDictionary state) throws FormatException {

		if (contactId.getInt() == state.getLong(CONTACT_ID_1).intValue())
			return new AuthorId(state.getRaw(AUTHOR_ID_2));
		if (contactId.getInt() == state.getLong(CONTACT_ID_2).intValue())
			return new AuthorId(state.getRaw(AUTHOR_ID_1));
		throw new RuntimeException(
				"Contact not part of this introduction session");
	}

	private boolean concernsThisContact(ContactId contactId, MessageId messageId,
			BdfDictionary state) throws FormatException {

		if (contactId.getInt() == state.getLong(CONTACT_ID_1).intValue()) {
			return Arrays.equals(state.getRaw(RESPONSE_1, new byte[0]),
					messageId.getBytes());
		} else {
			return Arrays.equals(state.getRaw(RESPONSE_2, new byte[0]),
					messageId.getBytes());
		}
	}

	private BdfDictionary getSessionState(Transaction txn, GroupId groupId,
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
			return state;
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
					if (g.equals(groupId)) return state;
				}
			}
			if (warn && LOG.isLoggable(WARNING))
				LOG.warning("No session state found");
			throw new FormatException();
		}
	}

	private BdfDictionary getSessionState(Transaction txn, GroupId groupId,
			byte[] sessionId) throws DbException, FormatException {

		return getSessionState(txn, groupId, sessionId, true);
	}

	private void deleteMessage(Transaction txn, MessageId messageId)
			throws DbException {

		db.deleteMessage(txn, messageId);
		db.deleteMessageMetadata(txn, messageId);
	}

}
