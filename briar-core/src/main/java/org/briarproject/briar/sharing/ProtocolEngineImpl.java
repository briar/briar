package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.event.ContactLeftShareableEvent;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.briar.sharing.MessageType.ABORT;
import static org.briarproject.briar.sharing.MessageType.ACCEPT;
import static org.briarproject.briar.sharing.MessageType.DECLINE;
import static org.briarproject.briar.sharing.MessageType.INVITE;
import static org.briarproject.briar.sharing.MessageType.LEAVE;
import static org.briarproject.briar.sharing.SharingConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.briar.sharing.State.LOCAL_INVITED;
import static org.briarproject.briar.sharing.State.LOCAL_LEFT;
import static org.briarproject.briar.sharing.State.REMOTE_HANGING;
import static org.briarproject.briar.sharing.State.REMOTE_INVITED;
import static org.briarproject.briar.sharing.State.SHARING;
import static org.briarproject.briar.sharing.State.START;

@Immutable
@NotNullByDefault
abstract class ProtocolEngineImpl<S extends Shareable>
		implements ProtocolEngine<S> {

	protected final DatabaseComponent db;
	protected final ClientHelper clientHelper;
	protected final MessageParser<S> messageParser;

	private final MessageEncoder messageEncoder;
	private final MessageTracker messageTracker;
	private final Clock clock;

	ProtocolEngineImpl(DatabaseComponent db, ClientHelper clientHelper,
			MessageEncoder messageEncoder, MessageParser<S> messageParser,
			MessageTracker messageTracker, Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.messageEncoder = messageEncoder;
		this.messageParser = messageParser;
		this.messageTracker = messageTracker;
		this.clock = clock;
	}

	@Override
	public Session onInviteAction(Transaction txn, Session s,
			@Nullable String message, long timestamp) throws DbException {
		switch (s.getState()) {
			case START:
				return onLocalInvite(txn, s, message, timestamp);
			case LOCAL_INVITED:
			case REMOTE_INVITED:
			case SHARING:
			case LOCAL_LEFT:
			case REMOTE_HANGING:
				throw new ProtocolStateException(); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	private Session onLocalInvite(Transaction txn, Session s,
			@Nullable String message, long timestamp) throws DbException {
		// Send an INVITE message
		Message sent = sendInviteMessage(txn, s, message, timestamp);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);
		// Make the shareable visible to the contact
		try {
			setShareableVisibility(txn, s, VISIBLE);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Move to the REMOTE_INVITED state
		return new Session(REMOTE_INVITED, s.getContactGroupId(),
				s.getShareableId(), sent.getId(), s.getLastRemoteMessageId(),
				sent.getTimestamp(), s.getInviteTimestamp());
	}

	private Message sendInviteMessage(Transaction txn, Session s,
			@Nullable String message, long timestamp) throws DbException {
		Group g = db.getGroup(txn, s.getShareableId());
		BdfList descriptor;
		try {
			descriptor = clientHelper.toList(g.getDescriptor());
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group descriptor
		}
		long localTimestamp = Math.max(timestamp, getLocalTimestamp(s));
		Message m = messageEncoder
				.encodeInviteMessage(s.getContactGroupId(), localTimestamp,
						s.getLastLocalMessageId(), descriptor, message);
		sendMessage(txn, m, INVITE, s.getShareableId(), true);
		return m;
	}

	@Override
	public Session onAcceptAction(Transaction txn, Session s)
			throws DbException {
		switch (s.getState()) {
			case LOCAL_INVITED:
				return onLocalAccept(txn, s);
			case START:
			case REMOTE_INVITED:
			case SHARING:
			case LOCAL_LEFT:
			case REMOTE_HANGING:
				throw new ProtocolStateException(); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	private Session onLocalAccept(Transaction txn, Session s)
			throws DbException {
		// Mark the invite message unavailable to answer
		MessageId inviteId = s.getLastRemoteMessageId();
		if (inviteId == null) throw new IllegalStateException();
		markMessageAvailableToAnswer(txn, inviteId, false);
		// Mark the invite message as accepted
		markInvitationAccepted(txn, inviteId, true);
		// Send a ACCEPT message
		Message sent = sendAcceptMessage(txn, s);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);
		try {
			// Add and subscribe to the shareable
			addShareable(txn, inviteId);
			// Share the shareable with the contact
			setShareableVisibility(txn, s, SHARED);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Move to the SHARING state
		return new Session(SHARING, s.getContactGroupId(), s.getShareableId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp());
	}

	protected abstract void addShareable(Transaction txn, MessageId inviteId)
			throws DbException, FormatException;

	private Message sendAcceptMessage(Transaction txn, Session session)
			throws DbException {
		Message m = messageEncoder.encodeAcceptMessage(
				session.getContactGroupId(), session.getShareableId(),
				getLocalTimestamp(session), session.getLastLocalMessageId());
		sendMessage(txn, m, ACCEPT, session.getShareableId(), true);
		return m;
	}

	@Override
	public Session onDeclineAction(Transaction txn, Session s)
			throws DbException {
		switch (s.getState()) {
			case LOCAL_INVITED:
				return onLocalDecline(txn, s);
			case START:
			case REMOTE_INVITED:
			case SHARING:
			case LOCAL_LEFT:
			case REMOTE_HANGING:
				throw new ProtocolStateException(); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	private Session onLocalDecline(Transaction txn, Session s)
			throws DbException {
		// Mark the invite message unavailable to answer
		MessageId inviteId = s.getLastRemoteMessageId();
		if (inviteId == null) throw new IllegalStateException();
		markMessageAvailableToAnswer(txn, inviteId, false);
		// Send a DECLINE message
		Message sent = sendDeclineMessage(txn, s);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);
		// Move to the START state
		return new Session(START, s.getContactGroupId(), s.getShareableId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp());
	}

	private Message sendDeclineMessage(Transaction txn, Session session)
			throws DbException {
		Message m = messageEncoder.encodeDeclineMessage(
				session.getContactGroupId(), session.getShareableId(),
				getLocalTimestamp(session), session.getLastLocalMessageId());
		sendMessage(txn, m, DECLINE, session.getShareableId(), true);
		return m;
	}

	@Override
	public Session onLeaveAction(Transaction txn, Session s)
			throws DbException {
		switch (s.getState()) {
			case REMOTE_INVITED:
				return onLocalLeave(txn, s, REMOTE_HANGING);
			case SHARING:
				return onLocalLeave(txn, s, LOCAL_LEFT);
			case START:
			case LOCAL_INVITED:
			case LOCAL_LEFT:
			case REMOTE_HANGING:
				return s; // Ignored in this state
			default:
				throw new AssertionError();
		}
	}

	private Session onLocalLeave(Transaction txn, Session s, State nextState)
			throws DbException {
		try {
			// Stop sharing the shareable (not actually needed in REMOTE_LEFT)
			setShareableVisibility(txn, s, INVISIBLE);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Send a LEAVE message
		Message sent = sendLeaveMessage(txn, s);
		// Move to the next state
		return new Session(nextState, s.getContactGroupId(), s.getShareableId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp());
	}

	private Message sendLeaveMessage(Transaction txn, Session session)
			throws DbException {
		Message m = messageEncoder.encodeLeaveMessage(
				session.getContactGroupId(), session.getShareableId(),
				getLocalTimestamp(session), session.getLastLocalMessageId());
		sendMessage(txn, m, LEAVE, session.getShareableId(), false);
		return m;
	}

	@Override
	public Session onInviteMessage(Transaction txn, Session s,
			InviteMessage<S> m) throws DbException, FormatException {
		switch (s.getState()) {
			case START:
			case LOCAL_LEFT:
				return onRemoteInvite(txn, s, m, true, LOCAL_INVITED);
			case REMOTE_INVITED:
				return onRemoteInviteWhenInvited(txn, s, m);
			case REMOTE_HANGING:
				return onRemoteInvite(txn, s, m, false, LOCAL_LEFT);
			case LOCAL_INVITED:
			case SHARING:
				return abortWithMessage(txn, s); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	private Session onRemoteInvite(Transaction txn, Session s,
			InviteMessage<S> m, boolean available, State nextState)
			throws DbException, FormatException {
		// The timestamp must be higher than the last invite message, if any
		if (m.getTimestamp() <= s.getInviteTimestamp())
			return abortWithMessage(txn, s);
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abortWithMessage(txn, s);
		// Mark the invite message visible in the UI and (un)available to answer
		markMessageVisibleInUi(txn, m.getId(), true);
		markMessageAvailableToAnswer(txn, m.getId(), available);
		// Track the message
		messageTracker.trackMessage(txn, m.getContactGroupId(),
				m.getTimestamp(), false);
		// Broadcast an event
		ContactId contactId = getContactId(txn, s.getContactGroupId());
		txn.attach(getInvitationRequestReceivedEvent(m, contactId, available,
				false));
		// Move to the next state
		return new Session(nextState, s.getContactGroupId(), s.getShareableId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				m.getTimestamp());
	}

	private Session onRemoteInviteWhenInvited(Transaction txn, Session s,
			InviteMessage<S> m) throws DbException, FormatException {
		// The timestamp must be higher than the last invite message, if any
		if (m.getTimestamp() <= s.getInviteTimestamp())
			return abortWithMessage(txn, s);
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abortWithMessage(txn, s);
		// Mark the invite message visible in the UI and unavailable to answer
		markMessageVisibleInUi(txn, m.getId(), true);
		markMessageAvailableToAnswer(txn, m.getId(), false);
		// Track the message
		messageTracker.trackMessage(txn, m.getContactGroupId(),
				m.getTimestamp(), false);
		// Share the shareable with the contact
		setShareableVisibility(txn, s, SHARED);
		// Broadcast an event
		ContactId contactId = getContactId(txn, s.getContactGroupId());
		txn.attach(
				getInvitationRequestReceivedEvent(m, contactId, false, true));
		// Move to the next state
		return new Session(SHARING, s.getContactGroupId(), s.getShareableId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				m.getTimestamp());
	}

	abstract Event getInvitationRequestReceivedEvent(InviteMessage<S> m,
			ContactId contactId, boolean available, boolean canBeOpened);

	@Override
	public Session onAcceptMessage(Transaction txn, Session s,
			AcceptMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case REMOTE_INVITED:
				return onRemoteAcceptWhenInvited(txn, s, m);
			case REMOTE_HANGING:
				return onRemoteAccept(txn, s, m, LOCAL_LEFT);
			case START:
			case LOCAL_INVITED:
			case SHARING:
			case LOCAL_LEFT:
				return abortWithMessage(txn, s); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	private Session onRemoteAccept(Transaction txn, Session s, AcceptMessage m,
			State nextState) throws DbException, FormatException {
		// The timestamp must be higher than the last invite message
		if (m.getTimestamp() <= s.getInviteTimestamp())
			return abortWithMessage(txn, s);
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abortWithMessage(txn, s);
		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getId(), true);
		// Track the message
		messageTracker.trackMessage(txn, m.getContactGroupId(),
				m.getTimestamp(), false);
		// Broadcast an event
		ContactId contactId = getContactId(txn, m.getContactGroupId());
		txn.attach(getInvitationResponseReceivedEvent(m, contactId));
		// Move to the next state
		return new Session(nextState, s.getContactGroupId(), s.getShareableId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp());
	}

	private Session onRemoteAcceptWhenInvited(Transaction txn, Session s,
			AcceptMessage m) throws DbException, FormatException {
		// Perform normal remote accept validation and operation
		Session session = onRemoteAccept(txn, s, m, SHARING);
		// Share the shareable with the contact, if session was not reset
		if (session.getState() != START)
			setShareableVisibility(txn, s, SHARED);
		return session;
	}

	abstract Event getInvitationResponseReceivedEvent(AcceptMessage m,
			ContactId contactId);

	@Override
	public Session onDeclineMessage(Transaction txn, Session s,
			DeclineMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case REMOTE_INVITED:
			case REMOTE_HANGING:
				return onRemoteDecline(txn, s, m);
			case START:
			case LOCAL_INVITED:
			case SHARING:
			case LOCAL_LEFT:
				return abortWithMessage(txn, s); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	private Session onRemoteDecline(Transaction txn, Session s,
			DeclineMessage m) throws DbException, FormatException {
		// The timestamp must be higher than the last invite message
		if (m.getTimestamp() <= s.getInviteTimestamp())
			return abortWithMessage(txn, s);
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abortWithMessage(txn, s);
		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getId(), true);
		// Track the message
		messageTracker.trackMessage(txn, m.getContactGroupId(),
				m.getTimestamp(), false);
		// Make the shareable invisible (not actually needed in REMOTE_HANGING)
		try {
			setShareableVisibility(txn, s, INVISIBLE);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Broadcast an event
		ContactId contactId = getContactId(txn, m.getContactGroupId());
		txn.attach(getInvitationResponseReceivedEvent(m, contactId));
		// Move to the next state
		return new Session(START, s.getContactGroupId(), s.getShareableId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp());
	}

	abstract Event getInvitationResponseReceivedEvent(DeclineMessage m,
			ContactId contactId);

	@Override
	public Session onLeaveMessage(Transaction txn, Session s,
			LeaveMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case LOCAL_INVITED:
				return onRemoteLeaveWhenInvited(txn, s, m);
			case LOCAL_LEFT:
				return onRemoteLeaveWhenLocalLeft(txn, s, m);
			case SHARING:
				return onRemoteLeaveWhenSharing(txn, s, m);
			case START:
			case REMOTE_INVITED:
			case REMOTE_HANGING:
				return abortWithMessage(txn, s); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	private Session onRemoteLeaveWhenInvited(Transaction txn, Session s,
			LeaveMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abortWithMessage(txn, s);
		// Mark any invite messages in the session unavailable to answer
		markInvitesUnavailableToAnswer(txn, s);
		// Move to the next state
		return new Session(START, s.getContactGroupId(), s.getShareableId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp());
	}

	private Session onRemoteLeaveWhenLocalLeft(Transaction txn, Session s,
			LeaveMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abortWithMessage(txn, s);
		// Move to the next state
		return new Session(START, s.getContactGroupId(), s.getShareableId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp());
	}

	private Session onRemoteLeaveWhenSharing(Transaction txn, Session s,
			LeaveMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abortWithMessage(txn, s);
		// Broadcast event informing that contact left
		ContactId contactId = getContactId(txn, s.getContactGroupId());
		ContactLeftShareableEvent e =
				new ContactLeftShareableEvent(s.getShareableId(),
						contactId);
		txn.attach(e);
		// Stop sharing the shareable with the contact
		setShareableVisibility(txn, s, INVISIBLE);
		// Move to the next state
		return new Session(START, s.getContactGroupId(), s.getShareableId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp());
	}

	@Override
	public Session onAbortMessage(Transaction txn, Session s, AbortMessage m)
			throws DbException, FormatException {
		abort(txn, s);
		return new Session(START, s.getContactGroupId(), s.getShareableId(),
				null, m.getId(), 0, 0);
	}

	private void abort(Transaction txn, Session s)
			throws DbException, FormatException {
		// Mark any invite messages in the session unavailable to answer
		markInvitesUnavailableToAnswer(txn, s);
		// If we subscribe, make the shareable invisible to the contact
		if (isSubscribed(txn, s.getShareableId()))
			setShareableVisibility(txn, s, INVISIBLE);
	}

	private Session abortWithMessage(Transaction txn, Session s)
			throws DbException, FormatException {
		abort(txn, s);
		// Send an ABORT message
		Message sent = sendAbortMessage(txn, s);
		// Reset the session back to initial state
		return new Session(START, s.getContactGroupId(), s.getShareableId(),
				sent.getId(), null, 0, 0);
	}

	private void markInvitesUnavailableToAnswer(Transaction txn, Session s)
			throws DbException, FormatException {
		GroupId shareableId = s.getShareableId();
		BdfDictionary query =
				messageParser.getInvitesAvailableToAnswerQuery(shareableId);
		Map<MessageId, BdfDictionary> results =
				clientHelper.getMessageMetadataAsDictionary(txn,
						s.getContactGroupId(), query);
		for (MessageId m : results.keySet())
			markMessageAvailableToAnswer(txn, m, false);
	}

	private boolean isSubscribed(Transaction txn, GroupId g)
			throws DbException {
		if (!db.containsGroup(txn, g)) return false;
		Group group = db.getGroup(txn, g);
		return group.getClientId().equals(getShareableClientId());
	}

	protected abstract ClientId getShareableClientId();

	private Message sendAbortMessage(Transaction txn, Session session)
			throws DbException {
		Message m = messageEncoder.encodeAbortMessage(
				session.getContactGroupId(), session.getShareableId(),
				getLocalTimestamp(session), session.getLastLocalMessageId());
		sendMessage(txn, m, ABORT, session.getShareableId(), false);
		return m;
	}

	private void sendMessage(Transaction txn, Message m, MessageType type,
			GroupId shareableId, boolean visibleInConversation)
			throws DbException {
		BdfDictionary meta = messageEncoder
				.encodeMetadata(type, shareableId, m.getTimestamp(), true, true,
						visibleInConversation, false, false);
		try {
			clientHelper.addLocalMessage(txn, m, meta, true);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private void markMessageAvailableToAnswer(Transaction txn, MessageId m,
			boolean available) throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.setAvailableToAnswer(meta, available);
		try {
			clientHelper.mergeMessageMetadata(txn, m, meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private void markMessageVisibleInUi(Transaction txn, MessageId m,
			boolean visible) throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.setVisibleInUi(meta, visible);
		try {
			clientHelper.mergeMessageMetadata(txn, m, meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private void markInvitationAccepted(Transaction txn, MessageId m,
			boolean accepted) throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.setInvitationAccepted(meta, accepted);
		try {
			clientHelper.mergeMessageMetadata(txn, m, meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private void setShareableVisibility(Transaction txn, Session session,
			Visibility v) throws DbException, FormatException {
		ContactId contactId = getContactId(txn, session.getContactGroupId());
		db.setGroupVisibility(txn, contactId, session.getShareableId(), v);
	}

	private ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException, FormatException {
		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn,
				contactGroupId);
		return new ContactId(meta.getLong(GROUP_KEY_CONTACT_ID).intValue());
	}

	private boolean isValidDependency(Session session,
			@Nullable MessageId dependency) {
		MessageId expected = session.getLastRemoteMessageId();
		if (dependency == null) return expected == null;
		return expected != null && dependency.equals(expected);
	}

	private long getLocalTimestamp(Session session) {
		return Math.max(clock.currentTimeMillis(),
				Math.max(session.getLocalTimestamp(),
						session.getInviteTimestamp()) + 1);
	}

}
