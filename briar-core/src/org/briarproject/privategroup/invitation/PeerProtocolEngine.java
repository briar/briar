package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ProtocolStateException;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.privategroup.invitation.PeerState.AWAIT_MEMBER;
import static org.briarproject.privategroup.invitation.PeerState.BOTH_JOINED;
import static org.briarproject.privategroup.invitation.PeerState.ERROR;
import static org.briarproject.privategroup.invitation.PeerState.LOCAL_JOINED;
import static org.briarproject.privategroup.invitation.PeerState.NEITHER_JOINED;
import static org.briarproject.privategroup.invitation.PeerState.REMOTE_JOINED;
import static org.briarproject.privategroup.invitation.PeerState.START;

@Immutable
@NotNullByDefault
class PeerProtocolEngine extends AbstractProtocolEngine<PeerSession> {

	PeerProtocolEngine(DatabaseComponent db, ClientHelper clientHelper,
			PrivateGroupManager privateGroupManager,
			PrivateGroupFactory privateGroupFactory,
			GroupMessageFactory groupMessageFactory,
			IdentityManager identityManager, MessageParser messageParser,
			MessageEncoder messageEncoder, Clock clock) {
		super(db, clientHelper, privateGroupManager, privateGroupFactory,
				groupMessageFactory, identityManager, messageParser,
				messageEncoder, clock);
	}

	@Override
	public PeerSession onInviteAction(Transaction txn, PeerSession s,
			@Nullable String message, long timestamp, byte[] signature)
			throws DbException {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	@Override
	public PeerSession onJoinAction(Transaction txn, PeerSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
			case AWAIT_MEMBER:
			case LOCAL_JOINED:
			case BOTH_JOINED:
			case ERROR:
				throw new ProtocolStateException(); // Invalid in these states
			case NEITHER_JOINED:
				return onLocalJoinFirst(txn, s);
			case REMOTE_JOINED:
				return onLocalJoinSecond(txn, s);
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onLeaveAction(Transaction txn, PeerSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
			case AWAIT_MEMBER:
			case NEITHER_JOINED:
			case REMOTE_JOINED:
			case ERROR:
				return s; // Ignored in these states
			case LOCAL_JOINED:
				return onLocalLeaveSecond(txn, s);
			case BOTH_JOINED:
				return onLocalLeaveFirst(txn, s);
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onMemberAddedAction(Transaction txn, PeerSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
			case AWAIT_MEMBER:
				return onRemoteMemberAdded(s);
			case NEITHER_JOINED:
			case LOCAL_JOINED:
			case REMOTE_JOINED:
			case BOTH_JOINED:
			case ERROR:
				throw new ProtocolStateException(); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onInviteMessage(Transaction txn, PeerSession s,
			InviteMessage m) throws DbException, FormatException {
		return abort(txn, s); // Invalid in this role
	}

	@Override
	public PeerSession onJoinMessage(Transaction txn, PeerSession s,
			JoinMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case AWAIT_MEMBER:
			case REMOTE_JOINED:
			case BOTH_JOINED:
				return abort(txn, s); // Invalid in these states
			case START:
			case NEITHER_JOINED:
				return onRemoteJoinFirst(txn, s, m);
			case LOCAL_JOINED:
				return onRemoteJoinSecond(txn, s, m);
			case ERROR:
				return s; // Ignored in this state
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onLeaveMessage(Transaction txn, PeerSession s,
			LeaveMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case START:
			case NEITHER_JOINED:
			case LOCAL_JOINED:
				return abort(txn, s);
			case AWAIT_MEMBER:
			case REMOTE_JOINED:
				return onRemoteLeaveSecond(txn, s, m);
			case BOTH_JOINED:
				return onRemoteLeaveFirst(txn, s, m);
			case ERROR:
				return s; // Ignored in this state
			default:
				throw new AssertionError();
		}
	}

	@Override
	public PeerSession onAbortMessage(Transaction txn, PeerSession s,
			AbortMessage m) throws DbException, FormatException {
		return abort(txn, s);
	}

	private PeerSession onLocalJoinFirst(Transaction txn, PeerSession s)
			throws DbException {
		// Send a JOIN message
		Message sent = sendJoinMessage(txn, s, false);
		// Move to the LOCAL_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				LOCAL_JOINED);
	}

	private PeerSession onLocalJoinSecond(Transaction txn, PeerSession s)
			throws DbException {
		// Send a JOIN message
		Message sent = sendJoinMessage(txn, s, false);
		try {
			// Start syncing the private group with the contact
			syncPrivateGroupWithContact(txn, s, true);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Move to the BOTH_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				BOTH_JOINED);
	}

	private PeerSession onLocalLeaveFirst(Transaction txn, PeerSession s)
			throws DbException {
		// Send a LEAVE message
		Message sent = sendLeaveMessage(txn, s, false);
		try {
			// Stop syncing the private group with the contact
			syncPrivateGroupWithContact(txn, s, false);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Move to the REMOTE_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				REMOTE_JOINED);
	}

	private PeerSession onLocalLeaveSecond(Transaction txn, PeerSession s)
			throws DbException {
		// Send a LEAVE message
		Message sent = sendLeaveMessage(txn, s, false);
		// Move to the NEITHER_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				NEITHER_JOINED);
	}

	private PeerSession onRemoteMemberAdded(PeerSession s) {
		// Move to the NEITHER_JOINED or REMOTE_JOINED state
		PeerState next = s.getState() == START ? NEITHER_JOINED : REMOTE_JOINED;
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), s.getLastRemoteMessageId(),
				s.getLocalTimestamp(), next);
	}

	private PeerSession onRemoteJoinFirst(Transaction txn, PeerSession s,
			JoinMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Move to the AWAIT_MEMBER or REMOTE_JOINED state
		PeerState next = s.getState() == START ? AWAIT_MEMBER : REMOTE_JOINED;
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				next);
	}

	private PeerSession onRemoteJoinSecond(Transaction txn, PeerSession s,
			JoinMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Start syncing the private group with the contact
		syncPrivateGroupWithContact(txn, s, true);
		// Move to the BOTH_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				BOTH_JOINED);
	}

	private PeerSession onRemoteLeaveSecond(Transaction txn, PeerSession s,
			LeaveMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Move to the START or NEITHER_JOINED state
		PeerState next = s.getState() == AWAIT_MEMBER ? START : NEITHER_JOINED;
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				next);
	}

	private PeerSession onRemoteLeaveFirst(Transaction txn, PeerSession s,
			LeaveMessage m) throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		// Stop syncing the private group with the contact
		syncPrivateGroupWithContact(txn, s, false);
		// Move to the LOCAL_JOINED state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				LOCAL_JOINED);
	}

	private PeerSession abort(Transaction txn, PeerSession s)
			throws DbException, FormatException {
		// If the session has already been aborted, do nothing
		if (s.getState() == ERROR) return s;
		// Stop syncing the private group with the contact, if we subscribe
		if (isSubscribedPrivateGroup(txn, s.getPrivateGroupId()))
			syncPrivateGroupWithContact(txn, s, false);
		// Send an ABORT message
		Message sent = sendAbortMessage(txn, s);
		// Move to the ERROR state
		return new PeerSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				ERROR);
	}
}
