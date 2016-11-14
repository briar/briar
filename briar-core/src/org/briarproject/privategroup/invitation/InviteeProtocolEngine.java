package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.ProtocolStateException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.GroupInvitationRequestReceivedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.privategroup.invitation.InviteeState.DISSOLVED;
import static org.briarproject.privategroup.invitation.InviteeState.ERROR;
import static org.briarproject.privategroup.invitation.InviteeState.INVITED;
import static org.briarproject.privategroup.invitation.InviteeState.INVITEE_JOINED;
import static org.briarproject.privategroup.invitation.InviteeState.INVITEE_LEFT;
import static org.briarproject.privategroup.invitation.InviteeState.START;

@Immutable
@NotNullByDefault
class InviteeProtocolEngine extends AbstractProtocolEngine<InviteeSession> {

	InviteeProtocolEngine(DatabaseComponent db, ClientHelper clientHelper,
			PrivateGroupManager privateGroupManager,
			PrivateGroupFactory privateGroupFactory,
			GroupMessageFactory groupMessageFactory,
			IdentityManager identityManager, MessageParser messageParser,
			MessageEncoder messageEncoder, MessageTracker messageTracker,
			Clock clock) {
		super(db, clientHelper, privateGroupManager, privateGroupFactory,
				groupMessageFactory, identityManager, messageParser,
				messageEncoder, messageTracker, clock);
	}

	@Override
	public InviteeSession onInviteAction(Transaction txn, InviteeSession s,
			@Nullable String message, long timestamp, byte[] signature)
			throws DbException {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	@Override
	public InviteeSession onJoinAction(Transaction txn, InviteeSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
			case INVITEE_JOINED:
			case INVITEE_LEFT:
			case DISSOLVED:
			case ERROR:
				throw new ProtocolStateException(); // Invalid in these states
			case INVITED:
				return onLocalAccept(txn, s);
			default:
				throw new AssertionError();
		}
	}

	@Override
	public InviteeSession onLeaveAction(Transaction txn, InviteeSession s)
			throws DbException {
		switch (s.getState()) {
			case START:
			case INVITEE_LEFT:
			case DISSOLVED:
			case ERROR:
				return s; // Ignored in these states
			case INVITED:
				return onLocalDecline(txn, s);
			case INVITEE_JOINED:
				return onLocalLeave(txn, s);
			default:
				throw new AssertionError();
		}
	}

	@Override
	public InviteeSession onMemberAddedAction(Transaction txn, InviteeSession s)
			throws DbException {
		return s; // Ignored in this role
	}

	@Override
	public InviteeSession onInviteMessage(Transaction txn, InviteeSession s,
			InviteMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case START:
				return onRemoteInvite(txn, s, m);
			case INVITED:
			case INVITEE_JOINED:
			case INVITEE_LEFT:
			case DISSOLVED:
				return abort(txn, s); // Invalid in these states
			case ERROR:
				return s; // Ignored in this state
			default:
				throw new AssertionError();
		}
	}

	@Override
	public InviteeSession onJoinMessage(Transaction txn, InviteeSession s,
			JoinMessage m) throws DbException, FormatException {
		return abort(txn, s); // Invalid in this role
	}

	@Override
	public InviteeSession onLeaveMessage(Transaction txn, InviteeSession s,
			LeaveMessage m) throws DbException, FormatException {
		switch (s.getState()) {
			case START:
			case DISSOLVED:
				return abort(txn, s); // Invalid in these states
			case INVITED:
			case INVITEE_JOINED:
			case INVITEE_LEFT:
				return onRemoteLeave(txn, s, m);
			case ERROR:
				return s; // Ignored in this state
			default:
				throw new AssertionError();
		}
	}

	@Override
	public InviteeSession onAbortMessage(Transaction txn, InviteeSession s,
			AbortMessage m) throws DbException, FormatException {
		return abort(txn, s);
	}

	private InviteeSession onLocalAccept(Transaction txn, InviteeSession s)
			throws DbException {
		// Mark the invite message unavailable to answer
		MessageId inviteId = s.getLastRemoteMessageId();
		if (inviteId == null) throw new IllegalStateException();
		markMessageAvailableToAnswer(txn, inviteId, false);
		// Send a JOIN message
		Message sent = sendJoinMessage(txn, s, true);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);
		try {
			// Subscribe to the private group
			subscribeToPrivateGroup(txn, inviteId);
			// Start syncing the private group with the contact
			syncPrivateGroupWithContact(txn, s, true);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Move to the INVITEE_JOINED state
		return new InviteeSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp(), INVITEE_JOINED);
	}

	private InviteeSession onLocalDecline(Transaction txn, InviteeSession s)
			throws DbException {
		// Mark the invite message unavailable to answer
		MessageId inviteId = s.getLastRemoteMessageId();
		if (inviteId == null) throw new IllegalStateException();
		markMessageAvailableToAnswer(txn, inviteId, false);
		// Send a LEAVE message
		Message sent = sendLeaveMessage(txn, s, true);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);
		// Move to the START state
		return new InviteeSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp(), START);
	}

	private InviteeSession onLocalLeave(Transaction txn, InviteeSession s)
			throws DbException {
		// Send a LEAVE message
		Message sent = sendLeaveMessage(txn, s, false);
		// Move to the INVITEE_LEFT state
		return new InviteeSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp(), INVITEE_LEFT);
	}

	private InviteeSession onRemoteInvite(Transaction txn, InviteeSession s,
			InviteMessage m) throws DbException, FormatException {
		// The timestamp must be higher than the last invite message, if any
		if (m.getTimestamp() <= s.getInviteTimestamp()) return abort(txn, s);
		// Check that the contact is the creator
		ContactId contactId = getContactId(txn, s.getContactGroupId());
		Author contact = db.getContact(txn, contactId).getAuthor();
		if (!contact.getId().equals(m.getCreator().getId()))
			return abort(txn, s);
		// Mark the invite message visible in the UI and available to answer
		markMessageVisibleInUi(txn, m.getId(), true);
		markMessageAvailableToAnswer(txn, m.getId(), true);
		// Track the message
		messageTracker.trackMessage(txn, m.getContactGroupId(),
				m.getTimestamp(), false);
		// Broadcast an event
		PrivateGroup privateGroup = privateGroupFactory.createPrivateGroup(
				m.getGroupName(), m.getCreator(), m.getSalt());
		txn.attach(new GroupInvitationRequestReceivedEvent(privateGroup,
				contactId, createInvitationRequest(m, contactId)));
		// Move to the INVITED state
		return new InviteeSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				m.getTimestamp(), INVITED);
	}

	private InviteeSession onRemoteLeave(Transaction txn, InviteeSession s,
			LeaveMessage m) throws DbException, FormatException {
		// The timestamp must be higher than the last invite message, if any
		if (m.getTimestamp() <= s.getInviteTimestamp()) return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (!isValidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);
		try {
			// Stop syncing the private group with the contact
			syncPrivateGroupWithContact(txn, s, false);
		} catch (FormatException e) {
			throw new DbException(e); // Invalid group metadata
		}
		// Mark the group dissolved
		privateGroupManager.markGroupDissolved(txn, s.getPrivateGroupId());
		// Move to the DISSOLVED state
		return new InviteeSession(s.getContactGroupId(), s.getPrivateGroupId(),
				s.getLastLocalMessageId(), m.getId(), s.getLocalTimestamp(),
				s.getInviteTimestamp(), DISSOLVED);
	}

	private InviteeSession abort(Transaction txn, InviteeSession s)
			throws DbException, FormatException {
		// If the session has already been aborted, do nothing
		if (s.getState() == ERROR) return s;
		// Mark any invite messages in the session unavailable to answer
		markInvitesUnavailableToAnswer(txn, s);
		// Stop syncing the private group with the contact, if we subscribe
		if (isSubscribedPrivateGroup(txn, s.getPrivateGroupId()))
			syncPrivateGroupWithContact(txn, s, false);
		// Send an ABORT message
		Message sent = sendAbortMessage(txn, s);
		// Move to the ERROR state
		return new InviteeSession(s.getContactGroupId(), s.getPrivateGroupId(),
				sent.getId(), s.getLastRemoteMessageId(), sent.getTimestamp(),
				s.getInviteTimestamp(), ERROR);
	}

	private GroupInvitationRequest createInvitationRequest(InviteMessage m,
			ContactId c) {
		SessionId sessionId = new SessionId(m.getPrivateGroupId().getBytes());
		return new GroupInvitationRequest(m.getId(), sessionId,
				m.getContactGroupId(), c, m.getMessage(), m.getGroupName(),
				m.getCreator(), true, m.getTimestamp(), false, false, true,
				false);
	}
}
