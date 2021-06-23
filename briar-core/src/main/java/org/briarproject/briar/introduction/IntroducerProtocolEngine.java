package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.api.introduction.event.IntroductionAbortedEvent;
import org.briarproject.briar.introduction.IntroducerSession.Introducee;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.lang.Math.max;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_ACTIVATES;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_ACTIVATE_A;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_ACTIVATE_B;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_AUTHS;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_AUTH_A;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_AUTH_B;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSES;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSE_A;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSE_B;
import static org.briarproject.briar.introduction.IntroducerState.A_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.B_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.START;

@Immutable
@NotNullByDefault
class IntroducerProtocolEngine
		extends AbstractProtocolEngine<IntroducerSession> {

	@Inject
	IntroducerProtocolEngine(
			DatabaseComponent db,
			ClientHelper clientHelper,
			ContactManager contactManager,
			ContactGroupFactory contactGroupFactory,
			MessageTracker messageTracker,
			IdentityManager identityManager,
			AuthorManager authorManager,
			MessageParser messageParser,
			MessageEncoder messageEncoder,
			ClientVersioningManager clientVersioningManager,
			AutoDeleteManager autoDeleteManager,
			ConversationManager conversationManager,
			Clock clock) {
		super(db, clientHelper, contactManager, contactGroupFactory,
				messageTracker, identityManager, authorManager, messageParser,
				messageEncoder, clientVersioningManager, autoDeleteManager,
				conversationManager, clock);
	}

	@Override
	public IntroducerSession onRequestAction(Transaction txn,
			IntroducerSession s, @Nullable String text)
			throws DbException {
		switch (s.getState()) {
			case START:
				return onLocalRequest(txn, s, text);
			case AWAIT_RESPONSES:
			case AWAIT_RESPONSE_A:
			case AWAIT_RESPONSE_B:
			case A_DECLINED:
			case B_DECLINED:
			case AWAIT_AUTHS:
			case AWAIT_AUTH_A:
			case AWAIT_AUTH_B:
			case AWAIT_ACTIVATES:
			case AWAIT_ACTIVATE_A:
			case AWAIT_ACTIVATE_B:
				throw new ProtocolStateException(); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroducerSession onAcceptAction(Transaction txn,
			IntroducerSession s) {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	@Override
	public IntroducerSession onDeclineAction(Transaction txn,
			IntroducerSession s, boolean isAutoDecline) {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	IntroducerSession onIntroduceeRemoved(Transaction txn,
			Introducee remainingIntroducee, IntroducerSession session)
			throws DbException {
		// abort session with remaining introducee
		IntroducerSession s = abort(txn, session, remainingIntroducee);
		return new IntroducerSession(s.getSessionId(), s.getState(),
				s.getRequestTimestamp(), s.getIntroduceeA(),
				s.getIntroduceeB());
	}

	@Override
	public IntroducerSession onRequestMessage(Transaction txn,
			IntroducerSession s, RequestMessage m) throws DbException {
		return abort(txn, s, m); // Invalid in this role
	}

	@Override
	public IntroducerSession onAcceptMessage(Transaction txn,
			IntroducerSession s, AcceptMessage m) throws DbException {
		switch (s.getState()) {
			case AWAIT_RESPONSES:
			case AWAIT_RESPONSE_A:
			case AWAIT_RESPONSE_B:
				return onRemoteAccept(txn, s, m);
			case A_DECLINED:
			case B_DECLINED:
				return onRemoteAcceptWhenDeclined(txn, s, m);
			case START:
			case AWAIT_AUTHS:
			case AWAIT_AUTH_A:
			case AWAIT_AUTH_B:
			case AWAIT_ACTIVATES:
			case AWAIT_ACTIVATE_A:
			case AWAIT_ACTIVATE_B:
				return abort(txn, s, m); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroducerSession onDeclineMessage(Transaction txn,
			IntroducerSession s, DeclineMessage m) throws DbException {
		switch (s.getState()) {
			case AWAIT_RESPONSES:
			case AWAIT_RESPONSE_A:
			case AWAIT_RESPONSE_B:
				return onRemoteDecline(txn, s, m);
			case A_DECLINED:
			case B_DECLINED:
				return onRemoteDeclineWhenDeclined(txn, s, m);
			case START:
			case AWAIT_AUTHS:
			case AWAIT_AUTH_A:
			case AWAIT_AUTH_B:
			case AWAIT_ACTIVATES:
			case AWAIT_ACTIVATE_A:
			case AWAIT_ACTIVATE_B:
				return abort(txn, s, m); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroducerSession onAuthMessage(Transaction txn, IntroducerSession s,
			AuthMessage m) throws DbException {
		switch (s.getState()) {
			case AWAIT_AUTHS:
			case AWAIT_AUTH_A:
			case AWAIT_AUTH_B:
				return onRemoteAuth(txn, s, m);
			case START:
			case AWAIT_RESPONSES:
			case AWAIT_RESPONSE_A:
			case AWAIT_RESPONSE_B:
			case A_DECLINED:
			case B_DECLINED:
			case AWAIT_ACTIVATES:
			case AWAIT_ACTIVATE_A:
			case AWAIT_ACTIVATE_B:
				return abort(txn, s, m); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroducerSession onActivateMessage(Transaction txn,
			IntroducerSession s, ActivateMessage m) throws DbException {
		switch (s.getState()) {
			case AWAIT_ACTIVATES:
			case AWAIT_ACTIVATE_A:
			case AWAIT_ACTIVATE_B:
				return onRemoteActivate(txn, s, m);
			case START:
			case AWAIT_RESPONSES:
			case AWAIT_RESPONSE_A:
			case AWAIT_RESPONSE_B:
			case A_DECLINED:
			case B_DECLINED:
			case AWAIT_AUTHS:
			case AWAIT_AUTH_A:
			case AWAIT_AUTH_B:
				return abort(txn, s, m); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroducerSession onAbortMessage(Transaction txn,
			IntroducerSession s, AbortMessage m) throws DbException {
		return onRemoteAbort(txn, s, m);
	}

	private IntroducerSession onLocalRequest(Transaction txn,
			IntroducerSession s, @Nullable String text) throws DbException {
		// Send REQUEST messages
		long timestampA =
				getTimestampForVisibleMessage(txn, s, s.getIntroduceeA());
		long timestampB =
				getTimestampForVisibleMessage(txn, s, s.getIntroduceeB());
		long localTimestamp = max(timestampA, timestampB);
		Message sentA = sendRequestMessage(txn, s.getIntroduceeA(),
				localTimestamp, s.getIntroduceeB().author, text);
		Message sentB = sendRequestMessage(txn, s.getIntroduceeB(),
				localTimestamp, s.getIntroduceeA().author, text);
		// Track the messages
		messageTracker.trackOutgoingMessage(txn, sentA);
		messageTracker.trackOutgoingMessage(txn, sentB);
		// Move to the AWAIT_RESPONSES state
		Introducee introduceeA = new Introducee(s.getIntroduceeA(), sentA);
		Introducee introduceeB = new Introducee(s.getIntroduceeB(), sentB);
		return new IntroducerSession(s.getSessionId(), AWAIT_RESPONSES,
				localTimestamp, introduceeA, introduceeB);
	}

	private IntroducerSession onRemoteAccept(Transaction txn,
			IntroducerSession s, AcceptMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s, m);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s, m);
		// The message must be expected in the current state
		boolean senderIsAlice = senderIsAlice(s, m);
		if (s.getState() != AWAIT_RESPONSES) {
			if (senderIsAlice && s.getState() != AWAIT_RESPONSE_A)
				return abort(txn, s, m);
			else if (!senderIsAlice && s.getState() != AWAIT_RESPONSE_B)
				return abort(txn, s, m);
		}

		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());
		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);
		// Receive the auto-delete timer
		receiveAutoDeleteTimer(txn, m);

		// Forward ACCEPT message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		// The forwarded message will not be visible to the introducee
		long localTimestamp = getTimestampForInvisibleMessage(s, i);
		Message sent = sendAcceptMessage(txn, i, localTimestamp,
				m.getEphemeralPublicKey(), m.getAcceptTimestamp(),
				m.getTransportProperties(), false);

		// Create the next state
		IntroducerState state = AWAIT_AUTHS;
		Introducee introduceeA, introduceeB;
		Author sender, other;
		if (senderIsAlice) {
			if (s.getState() == AWAIT_RESPONSES) state = AWAIT_RESPONSE_B;
			introduceeA = new Introducee(s.getIntroduceeA(), m.getMessageId());
			introduceeB = new Introducee(s.getIntroduceeB(), sent);
			sender = introduceeA.author;
			other = introduceeB.author;
		} else {
			if (s.getState() == AWAIT_RESPONSES) state = AWAIT_RESPONSE_A;
			introduceeA = new Introducee(s.getIntroduceeA(), sent);
			introduceeB = new Introducee(s.getIntroduceeB(), m.getMessageId());
			sender = introduceeB.author;
			other = introduceeA.author;
		}

		// Broadcast IntroductionResponseReceivedEvent
		broadcastIntroductionResponseReceivedEvent(txn, s, sender.getId(),
				other, m, true);

		// Move to the next state
		return new IntroducerSession(s.getSessionId(), state,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private boolean senderIsAlice(IntroducerSession s,
			AbstractIntroductionMessage m) {
		return m.getGroupId().equals(s.getIntroduceeA().groupId);
	}

	private IntroducerSession onRemoteAcceptWhenDeclined(Transaction txn,
			IntroducerSession s, AcceptMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s, m);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s, m);
		// The message must be expected in the current state
		boolean senderIsAlice = senderIsAlice(s, m);
		if (senderIsAlice && s.getState() != B_DECLINED)
			return abort(txn, s, m);
		else if (!senderIsAlice && s.getState() != A_DECLINED)
			return abort(txn, s, m);

		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());
		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);
		// Receive the auto-delete timer
		receiveAutoDeleteTimer(txn, m);

		// Forward ACCEPT message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		// The forwarded message will not be visible to the introducee
		long localTimestamp = getTimestampForInvisibleMessage(s, i);
		Message sent = sendAcceptMessage(txn, i, localTimestamp,
				m.getEphemeralPublicKey(), m.getAcceptTimestamp(),
				m.getTransportProperties(), false);

		Introducee introduceeA, introduceeB;
		Author sender, other;
		if (senderIsAlice) {
			introduceeA = new Introducee(s.getIntroduceeA(), m.getMessageId());
			introduceeB = new Introducee(s.getIntroduceeB(), sent);
			sender = introduceeA.author;
			other = introduceeB.author;
		} else {
			introduceeA = new Introducee(s.getIntroduceeA(), sent);
			introduceeB = new Introducee(s.getIntroduceeB(), m.getMessageId());
			sender = introduceeB.author;
			other = introduceeA.author;
		}

		// Broadcast IntroductionResponseReceivedEvent
		broadcastIntroductionResponseReceivedEvent(txn, s, sender.getId(),
				other, m, false);

		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private IntroducerSession onRemoteDecline(Transaction txn,
			IntroducerSession s, DeclineMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s, m);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s, m);
		// The message must be expected in the current state
		boolean senderIsAlice = senderIsAlice(s, m);
		if (s.getState() != AWAIT_RESPONSES) {
			if (senderIsAlice && s.getState() != AWAIT_RESPONSE_A)
				return abort(txn, s, m);
			else if (!senderIsAlice && s.getState() != AWAIT_RESPONSE_B)
				return abort(txn, s, m);
		}

		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());
		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);
		// Receive the auto-delete timer
		receiveAutoDeleteTimer(txn, m);

		// Forward DECLINE message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		// The forwarded message will be visible to the introducee
		long localTimestamp = getTimestampForVisibleMessage(txn, s, i);
		Message sent = sendDeclineMessage(txn, i, localTimestamp, false, false);

		// Create the next state
		IntroducerState state = START;
		Introducee introduceeA, introduceeB;
		Author sender, other;
		if (senderIsAlice) {
			if (s.getState() == AWAIT_RESPONSES) state = A_DECLINED;
			introduceeA = new Introducee(s.getIntroduceeA(), m.getMessageId());
			introduceeB = new Introducee(s.getIntroduceeB(), sent);
			sender = introduceeA.author;
			other = introduceeB.author;
		} else {
			if (s.getState() == AWAIT_RESPONSES) state = B_DECLINED;
			introduceeA = new Introducee(s.getIntroduceeA(), sent);
			introduceeB = new Introducee(s.getIntroduceeB(), m.getMessageId());
			sender = introduceeB.author;
			other = introduceeA.author;
		}

		// Broadcast IntroductionResponseReceivedEvent
		broadcastIntroductionResponseReceivedEvent(txn, s, sender.getId(),
				other, m, false);

		return new IntroducerSession(s.getSessionId(), state,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private IntroducerSession onRemoteDeclineWhenDeclined(Transaction txn,
			IntroducerSession s, DeclineMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s, m);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s, m);
		// The message must be expected in the current state
		boolean senderIsAlice = senderIsAlice(s, m);
		if (senderIsAlice && s.getState() != B_DECLINED)
			return abort(txn, s, m);
		else if (!senderIsAlice && s.getState() != A_DECLINED)
			return abort(txn, s, m);

		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());
		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);
		// Receive the auto-delete timer
		receiveAutoDeleteTimer(txn, m);

		// Forward DECLINE message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		// The forwarded message will be visible to the introducee
		long localTimestamp = getTimestampForVisibleMessage(txn, s, i);
		Message sent = sendDeclineMessage(txn, i, localTimestamp, false, false);

		Introducee introduceeA, introduceeB;
		Author sender, other;
		if (senderIsAlice) {
			introduceeA = new Introducee(s.getIntroduceeA(), m.getMessageId());
			introduceeB = new Introducee(s.getIntroduceeB(), sent);
			sender = introduceeA.author;
			other = introduceeB.author;
		} else {
			introduceeA = new Introducee(s.getIntroduceeA(), sent);
			introduceeB = new Introducee(s.getIntroduceeB(), m.getMessageId());
			sender = introduceeB.author;
			other = introduceeA.author;
		}

		// Broadcast IntroductionResponseReceivedEvent
		broadcastIntroductionResponseReceivedEvent(txn, s, sender.getId(),
				other, m, false);

		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private IntroducerSession onRemoteAuth(Transaction txn,
			IntroducerSession s, AuthMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s, m);
		// The message must be expected in the current state
		boolean senderIsAlice = senderIsAlice(s, m);
		if (s.getState() != AWAIT_AUTHS) {
			if (senderIsAlice && s.getState() != AWAIT_AUTH_A)
				return abort(txn, s, m);
			else if (!senderIsAlice && s.getState() != AWAIT_AUTH_B)
				return abort(txn, s, m);
		}

		// Forward AUTH message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		long localTimestamp = getTimestampForInvisibleMessage(s, i);
		Message sent = sendAuthMessage(txn, i, localTimestamp, m.getMac(),
				m.getSignature());

		// Move to the next state
		IntroducerState state = AWAIT_ACTIVATES;
		Introducee introduceeA, introduceeB;
		if (senderIsAlice) {
			if (s.getState() == AWAIT_AUTHS) state = AWAIT_AUTH_B;
			introduceeA = new Introducee(s.getIntroduceeA(), m.getMessageId());
			introduceeB = new Introducee(s.getIntroduceeB(), sent);
		} else {
			if (s.getState() == AWAIT_AUTHS) state = AWAIT_AUTH_A;
			introduceeA = new Introducee(s.getIntroduceeA(), sent);
			introduceeB = new Introducee(s.getIntroduceeB(), m.getMessageId());
		}
		return new IntroducerSession(s.getSessionId(), state,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private IntroducerSession onRemoteActivate(Transaction txn,
			IntroducerSession s, ActivateMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s, m);
		// The message must be expected in the current state
		boolean senderIsAlice = senderIsAlice(s, m);
		if (s.getState() != AWAIT_ACTIVATES) {
			if (senderIsAlice && s.getState() != AWAIT_ACTIVATE_A)
				return abort(txn, s, m);
			else if (!senderIsAlice && s.getState() != AWAIT_ACTIVATE_B)
				return abort(txn, s, m);
		}

		// Forward ACTIVATE message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		long localTimestamp = getTimestampForInvisibleMessage(s, i);
		Message sent = sendActivateMessage(txn, i, localTimestamp, m.getMac());

		// Move to the next state
		IntroducerState state = START;
		Introducee introduceeA, introduceeB;
		if (senderIsAlice) {
			if (s.getState() == AWAIT_ACTIVATES) state = AWAIT_ACTIVATE_B;
			introduceeA = new Introducee(s.getIntroduceeA(), m.getMessageId());
			introduceeB = new Introducee(s.getIntroduceeB(), sent);
		} else {
			if (s.getState() == AWAIT_ACTIVATES) state = AWAIT_ACTIVATE_A;
			introduceeA = new Introducee(s.getIntroduceeA(), sent);
			introduceeB = new Introducee(s.getIntroduceeB(), m.getMessageId());
		}
		return new IntroducerSession(s.getSessionId(), state,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private IntroducerSession onRemoteAbort(Transaction txn,
			IntroducerSession s, AbortMessage m) throws DbException {
		// Forward ABORT message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		long localTimestamp = getTimestampForInvisibleMessage(s, i);
		Message sent = sendAbortMessage(txn, i, localTimestamp);

		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Reset the session back to initial state
		Introducee introduceeA, introduceeB;
		if (i.equals(s.getIntroduceeA())) {
			introduceeA = new Introducee(s.getIntroduceeA(), sent);
			introduceeB = new Introducee(s.getIntroduceeB(), m.getMessageId());
		} else if (i.equals(s.getIntroduceeB())) {
			introduceeA = new Introducee(s.getIntroduceeA(), m.getMessageId());
			introduceeB = new Introducee(s.getIntroduceeB(), sent);
		} else throw new AssertionError();
		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private IntroducerSession abort(Transaction txn, IntroducerSession s,
			Introducee remainingIntroducee) throws DbException {
		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Send an ABORT message to the remaining introducee
		long localTimestamp =
				getTimestampForInvisibleMessage(s, remainingIntroducee);
		Message sent =
				sendAbortMessage(txn, remainingIntroducee, localTimestamp);
		// Reset the session back to initial state
		Introducee introduceeA = s.getIntroduceeA();
		Introducee introduceeB = s.getIntroduceeB();
		if (remainingIntroducee.author.equals(introduceeA.author)) {
			introduceeA = new Introducee(introduceeA, sent);
			introduceeB = new Introducee(s.getSessionId(), introduceeB.groupId,
					introduceeB.author);
		} else if (remainingIntroducee.author.equals(introduceeB.author)) {
			introduceeA = new Introducee(s.getSessionId(), introduceeA.groupId,
					introduceeA.author);
			introduceeB = new Introducee(introduceeB, sent);
		} else {
			throw new DbException();
		}
		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private IntroducerSession abort(Transaction txn, IntroducerSession s,
			AbstractIntroductionMessage lastRemoteMessage) throws DbException {
		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Record the message that triggered the abort
		Introducee introduceeA = s.getIntroduceeA();
		Introducee introduceeB = s.getIntroduceeB();
		if (senderIsAlice(s, lastRemoteMessage)) {
			introduceeA = new Introducee(introduceeA,
					lastRemoteMessage.getMessageId());
		} else {
			introduceeB = new Introducee(introduceeB,
					lastRemoteMessage.getMessageId());
		}

		// Send an ABORT message to both introducees
		long timestampA = getTimestampForInvisibleMessage(s, introduceeA);
		Message sentA = sendAbortMessage(txn, introduceeA, timestampA);
		long timestampB = getTimestampForInvisibleMessage(s, introduceeB);
		Message sentB = sendAbortMessage(txn, introduceeB, timestampB);

		// Reset the session back to initial state
		introduceeA = new Introducee(introduceeA, sentA);
		introduceeB = new Introducee(introduceeB, sentB);
		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introduceeA, introduceeB);
	}

	private Introducee getIntroducee(IntroducerSession s, GroupId g) {
		if (s.getIntroduceeA().groupId.equals(g)) return s.getIntroduceeA();
		else if (s.getIntroduceeB().groupId.equals(g))
			return s.getIntroduceeB();
		else throw new AssertionError();
	}

	private Introducee getOtherIntroducee(IntroducerSession s, GroupId g) {
		if (s.getIntroduceeA().groupId.equals(g)) return s.getIntroduceeB();
		else if (s.getIntroduceeB().groupId.equals(g))
			return s.getIntroduceeA();
		else throw new AssertionError();
	}

	private boolean isInvalidDependency(IntroducerSession session,
			GroupId contactGroupId, @Nullable MessageId dependency) {
		MessageId expected =
				getIntroducee(session, contactGroupId).lastRemoteMessageId;
		return isInvalidDependency(expected, dependency);
	}

	/**
	 * Returns a timestamp for a visible outgoing message. The timestamp is
	 * later than the timestamp of any message sent or received so far in the
	 * conversation, and later than the {@link
	 * #getSessionTimestamp(IntroducerSession, PeerSession) session timestamp}.
	 */
	private long getTimestampForVisibleMessage(Transaction txn,
			IntroducerSession s, PeerSession p) throws DbException {
		long conversationTimestamp =
				getTimestampForOutgoingMessage(txn, p.getContactGroupId());
		return max(conversationTimestamp, getSessionTimestamp(s, p) + 1);
	}

	/**
	 * Returns a timestamp for an invisible outgoing message. The timestamp is
	 * later than the {@link #getSessionTimestamp(IntroducerSession, PeerSession)
	 * session timestamp}.
	 */
	private long getTimestampForInvisibleMessage(IntroducerSession s,
			PeerSession p) {
		return max(clock.currentTimeMillis(), getSessionTimestamp(s, p) + 1);
	}

	/**
	 * Returns the latest timestamp of any message sent so far in the session.
	 */
	private long getSessionTimestamp(IntroducerSession s, PeerSession p) {
		return max(p.getLocalTimestamp(), s.getRequestTimestamp());
	}
}
