package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.contact.ContactManager.AddContactHook;
import org.briarproject.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.api.crypto.CryptoComponent;
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
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.introduction.IntroducerProtocolState;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.introduction.IntroductionMessage;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.IntroductionResponse;
import org.briarproject.api.introduction.SessionId;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.ANSWERED;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.AUTHOR_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_2;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_2;
import static org.briarproject.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.READ;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.RESPONSE_1;
import static org.briarproject.api.introduction.IntroductionConstants.RESPONSE_2;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;

class IntroductionManagerImpl extends BdfIncomingMessageHook
		implements IntroductionManager, AddContactHook, RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"23b1897c198a90ae75b976ac023d0f32"
					+ "80ca67b12f2346b2c23a34f34e2434c3"));

	private static final byte[] LOCAL_GROUP_DESCRIPTOR = new byte[0];

	private static final Logger LOG =
			Logger.getLogger(IntroductionManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final MessageQueueManager messageQueueManager;
	private final PrivateGroupFactory privateGroupFactory;
	private final MetadataEncoder metadataEncoder;
	private final IntroducerManager introducerManager;
	private final IntroduceeManager introduceeManager;
	private final Group localGroup;

	@Inject
	IntroductionManagerImpl(DatabaseComponent db,
			MessageQueueManager messageQueueManager,
			ClientHelper clientHelper, GroupFactory groupFactory,
			PrivateGroupFactory privateGroupFactory,
			MetadataEncoder metadataEncoder, MetadataParser metadataParser,
			CryptoComponent cryptoComponent,
			TransportPropertyManager transportPropertyManager,
			AuthorFactory authorFactory, ContactManager contactManager,
			Clock clock) {

		super(clientHelper, metadataParser, clock);
		this.db = db;
		this.messageQueueManager = messageQueueManager;
		this.privateGroupFactory = privateGroupFactory;
		this.metadataEncoder = metadataEncoder;
		this.introducerManager =
				new IntroducerManager(this, clientHelper, clock,
						cryptoComponent);
		this.introduceeManager =
				new IntroduceeManager(db, this, clientHelper, clock,
						cryptoComponent, transportPropertyManager,
						authorFactory, contactManager);
		localGroup =
				groupFactory.createGroup(CLIENT_ID, LOCAL_GROUP_DESCRIPTOR);
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		try {
			// create an introduction group for sending introduction messages
			Group g = getIntroductionGroup(c);
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
		// check for open sessions with that contact and abort those
		Long id = (long) c.getId().getInt();
		try {
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn, localGroup.getId());
			for (Map.Entry<MessageId, BdfDictionary> entry : map.entrySet()) {
				BdfDictionary d = entry.getValue();
				long role = d.getLong(ROLE, -1L);
				if (role != ROLE_INTRODUCER) continue;
				if (d.getLong(CONTACT_ID_1).equals(id) ||
						d.getLong(CONTACT_ID_2).equals(id)) {

					IntroducerProtocolState state = IntroducerProtocolState
							.fromValue(d.getLong(STATE).intValue());
					if (IntroducerProtocolState.isOngoing(state)) {
						introducerManager.abort(txn, d);
					}
				}

			}
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}

		// remove the group (all messages will be removed with it)
		// this contact won't get our abort message, but the other will
		db.removeGroup(txn, getIntroductionGroup(c));
	}

	/**
	 * This is called when a new message arrived and is being validated.
	 * It is the central method where we determine which role we play
	 * in the introduction protocol and which engine we need to start.
	 */
	@Override
	protected void incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary message)	throws DbException {

		// add local group for engine states to make sure it exists
		db.addGroup(txn, localGroup);

		// Get message data and type
		GroupId groupId = m.getGroupId();
		message.put(GROUP_ID, groupId);
		long type = message.getLong(TYPE, -1L);

		// we are an introducee, need to initialize new state
		if (type == TYPE_REQUEST) {
			BdfDictionary state;
			try {
				state = introduceeManager.initialize(txn, groupId, message);
			} catch (FormatException e) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Could not initialize introducee state");
					LOG.log(WARNING, e.toString(), e);
				}
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
			BdfDictionary state;
			try {
				state = getSessionState(txn,
						message.getRaw(SESSION_ID, new byte[0]));
			} catch (FormatException e) {
				LOG.warning("Could not find state for message, deleting...");
				deleteMessage(txn, m.getId());
				return;
			}

			long role = state.getLong(ROLE, -1L);
			try {
				if (role == ROLE_INTRODUCER) {
					introducerManager.incomingMessage(txn, state, message);
				} else if (role == ROLE_INTRODUCEE) {
					introduceeManager.incomingMessage(txn, state, message);
				} else {
					if(LOG.isLoggable(WARNING)) {
						LOG.warning("Unknown role '" + role +
								"'. Deleting message...");
						deleteMessage(txn, m.getId());
					}
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				if (role == ROLE_INTRODUCER) introducerManager.abort(txn, state);
				else introduceeManager.abort(txn, state);
			} catch (IOException e) {
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
	}

	@Override
	public void makeIntroduction(Contact c1, Contact c2, String msg)
			throws DbException, FormatException {

			Transaction txn = db.startTransaction(false);
			try {
				// add local group for session states to make sure it exists
				db.addGroup(txn, getLocalGroup());
				introducerManager.makeIntroduction(txn, c1, c2, msg);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
	}

	@Override
	public void acceptIntroduction(final SessionId sessionId)
			throws DbException, FormatException {

		Transaction txn = db.startTransaction(false);
		try {
			introduceeManager.acceptIntroduction(txn, sessionId);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void declineIntroduction(final SessionId sessionId)
			throws DbException, FormatException {

		Transaction txn = db.startTransaction(false);
		try {
			introduceeManager.declineIntroduction(txn, sessionId);
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
			GroupId g =
					getIntroductionGroup(db.getContact(txn, contactId)).getId();
			metadata = clientHelper.getMessageMetadataAsDictionary(txn, g);
			statuses = db.getMessageStatus(txn, contactId, g);

			// turn messages into classes for the UI
			Map<SessionId, BdfDictionary> sessionStates =
					new HashMap<SessionId, BdfDictionary>();
			for (MessageStatus s : statuses) {
				MessageId messageId = s.getMessageId();
				BdfDictionary msg = metadata.get(messageId);
				if (msg == null) continue;

				try {
					long type = msg.getLong(TYPE);
					if (type == TYPE_ACK || type == TYPE_ABORT) continue;

					// get session state
					SessionId sessionId = new SessionId(msg.getRaw(SESSION_ID));
					BdfDictionary state = sessionStates.get(sessionId);
					if (state == null) {
						state = getSessionState(txn, sessionId.getBytes());
					}
					sessionStates.put(sessionId, state);

					boolean local;
					long time = msg.getLong(MESSAGE_TIME);
					boolean accepted = msg.getBoolean(ACCEPT, false);
					boolean read = msg.getBoolean(READ, false);
					AuthorId authorId;
					String name;
					if (type == TYPE_RESPONSE) {
						if (state.getLong(ROLE) == ROLE_INTRODUCER) {
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
								// this response is not ours, don't include it
								continue;
							}
							local = true;
							authorId = new AuthorId(
									state.getRaw(REMOTE_AUTHOR_ID));
							name = state.getString(NAME);
						}
						IntroductionResponse ir = new IntroductionResponse(
								sessionId, messageId, time, local, s.isSent(),
								s.isSeen(), read, authorId, name, accepted);
						list.add(ir);
					} else if (type == TYPE_REQUEST) {
						String message;
						boolean answered, exists;
						if (state.getLong(ROLE) == ROLE_INTRODUCER) {
							local = true;
							authorId =
									getAuthorIdForIntroducer(contactId, state);
							name = getNameForIntroducer(contactId, state);
							message = msg.getOptionalString(MSG);
							answered = false;
							exists = false;
						} else {
							local = false;
							authorId = new AuthorId(
									state.getRaw(REMOTE_AUTHOR_ID));
							name = state.getString(NAME);
							message = state.getOptionalString(MSG);
							answered = state.getBoolean(ANSWERED);
							exists = state.getBoolean(EXISTS);
						}
						IntroductionRequest ir = new IntroductionRequest(
								sessionId, messageId, time, local, s.isSent(),
								s.isSeen(), read, authorId, name, accepted,
								message, answered, exists);
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
			BdfDictionary state) throws FormatException {

		if (contactId.getInt() == state.getLong(CONTACT_ID_1).intValue())
			return state.getString(CONTACT_2);
		if (contactId.getInt() == state.getLong(CONTACT_ID_2).intValue())
			return state.getString(CONTACT_1);
		throw new RuntimeException("Contact not part of this introduction session");
	}

	private AuthorId getAuthorIdForIntroducer(ContactId contactId,
			BdfDictionary state) throws FormatException {

		if (contactId.getInt() == state.getLong(CONTACT_ID_1).intValue())
			return new AuthorId(state.getRaw(AUTHOR_ID_2));
		if (contactId.getInt() == state.getLong(CONTACT_ID_2).intValue())
			return new AuthorId(state.getRaw(AUTHOR_ID_1));
		throw new RuntimeException("Contact not part of this introduction session");
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

	@Override
	public void setReadFlag(MessageId m, boolean read) throws DbException {
		try {
			BdfDictionary meta = BdfDictionary.of(new BdfEntry(READ, read));
			clientHelper.mergeMessageMetadata(m, meta);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	public BdfDictionary getSessionState(Transaction txn, byte[] sessionId)
			throws DbException, FormatException {

		try {
			return clientHelper.getMessageMetadataAsDictionary(txn,
					new MessageId(sessionId));
		} catch (NoSuchMessageException e) {
			Map<MessageId, BdfDictionary> map = clientHelper
					.getMessageMetadataAsDictionary(txn,
							localGroup.getId());
			for (Map.Entry<MessageId, BdfDictionary> m : map.entrySet()) {
				if (Arrays.equals(m.getValue().getRaw(SESSION_ID), sessionId)) {
					return m.getValue();
				}
			}
			if (LOG.isLoggable(WARNING)) {
				LOG.warning(
						"No session state found for this message with session ID " +
								Arrays.hashCode(sessionId));
			}
			throw new FormatException();
		}
	}

	public Group getIntroductionGroup(Contact c) {
		return privateGroupFactory.createPrivateGroup(CLIENT_ID, c);
	}

	public Group getLocalGroup() {
		return localGroup;
	}

	public void sendMessage(Transaction txn, BdfDictionary message)
			throws DbException, FormatException {

		BdfList bdfList = MessageEncoder.encodeMessage(message);
		byte[] body = clientHelper.toByteArray(bdfList);
		GroupId groupId = new GroupId(message.getRaw(GROUP_ID));
		Group group = db.getGroup(txn, groupId);
		long timestamp = System.currentTimeMillis();

		message.put(MESSAGE_TIME, timestamp);
		Metadata metadata = metadataEncoder.encode(message);

		messageQueueManager
				.sendMessage(txn, group, timestamp, body, metadata);
	}

	private void deleteMessage(Transaction txn, MessageId messageId)
			throws DbException {

		db.deleteMessage(txn, messageId);
		db.deleteMessageMetadata(txn, messageId);
	}

}
