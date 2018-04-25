package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.introduction.event.IntroductionAbortedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionResponseReceivedEvent;
import org.briarproject.briar.introduction.IntroducerSession.Introducee;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.introduction.Role.INTRODUCER;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_ACTIVATES;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_ACTIVATE_A;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_ACTIVATE_B;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_AUTHS;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_AUTH_A;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_AUTH_B;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSES;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSE_A;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSE_B;
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
			MessageParser messageParser,
			MessageEncoder messageEncoder,
			Clock clock) {
		super(db, clientHelper, contactManager, contactGroupFactory,
				messageTracker, identityManager, messageParser, messageEncoder,
				clock);
	}

	@Override
	public IntroducerSession onRequestAction(Transaction txn,
			IntroducerSession s, @Nullable String message, long timestamp)
			throws DbException {
		switch (s.getState()) {
			case START:
				return onLocalRequest(txn, s, message, timestamp);
			case AWAIT_RESPONSES:
			case AWAIT_RESPONSE_A:
			case AWAIT_RESPONSE_B:
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
			IntroducerSession s, long timestamp) {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	@Override
	public IntroducerSession onDeclineAction(Transaction txn,
			IntroducerSession s, long timestamp) {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	IntroducerSession onAbortAction(Transaction txn, IntroducerSession s)
			throws DbException {
		return abort(txn, s);
	}

	@Override
	public IntroducerSession onRequestMessage(Transaction txn,
			IntroducerSession s, RequestMessage m) throws DbException {
		return abort(txn, s); // Invalid in this role
	}

	@Override
	public IntroducerSession onAcceptMessage(Transaction txn,
			IntroducerSession s, AcceptMessage m) throws DbException {
		switch (s.getState()) {
			case AWAIT_RESPONSES:
			case AWAIT_RESPONSE_A:
			case AWAIT_RESPONSE_B:
				return onRemoteAccept(txn, s, m);
			case START:
				return onRemoteResponseInStart(txn, s, m);
			case AWAIT_AUTHS:
			case AWAIT_AUTH_A:
			case AWAIT_AUTH_B:
			case AWAIT_ACTIVATES:
			case AWAIT_ACTIVATE_A:
			case AWAIT_ACTIVATE_B:
				return abort(txn, s); // Invalid in these states
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
			case START:
				return onRemoteResponseInStart(txn, s, m);
			case AWAIT_AUTHS:
			case AWAIT_AUTH_A:
			case AWAIT_AUTH_B:
			case AWAIT_ACTIVATES:
			case AWAIT_ACTIVATE_A:
			case AWAIT_ACTIVATE_B:
				return abort(txn, s); // Invalid in these states
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
			case AWAIT_ACTIVATES:
			case AWAIT_ACTIVATE_A:
			case AWAIT_ACTIVATE_B:
				return abort(txn, s); // Invalid in these states
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
			case AWAIT_AUTHS:
			case AWAIT_AUTH_A:
			case AWAIT_AUTH_B:
				return abort(txn, s); // Invalid in these states
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
			IntroducerSession s,
			@Nullable String message, long timestamp) throws DbException {
		// Send REQUEST messages
		long localTimestamp =
				Math.max(timestamp, getLocalTimestamp(s, s.getIntroducee1()));
		Message sent1 = sendRequestMessage(txn, s.getIntroducee1(),
				localTimestamp, s.getIntroducee2().author, message
		);
		Message sent2 = sendRequestMessage(txn, s.getIntroducee2(),
				localTimestamp, s.getIntroducee1().author, message
		);
		// Track the messages
		messageTracker.trackOutgoingMessage(txn, sent1);
		messageTracker.trackOutgoingMessage(txn, sent2);
		// Move to the AWAIT_RESPONSES state
		Introducee introducee1 = new Introducee(s.getIntroducee1(), sent1);
		Introducee introducee2 = new Introducee(s.getIntroducee2(), sent2);
		return new IntroducerSession(s.getSessionId(), AWAIT_RESPONSES,
				localTimestamp, introducee1, introducee2);
	}

	private IntroducerSession onRemoteAccept(Transaction txn,
			IntroducerSession s, AcceptMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s);

		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());
		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);

		// Forward ACCEPT message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		long timestamp = getLocalTimestamp(s, i);
		Message sent =
				sendAcceptMessage(txn, i, timestamp, m.getEphemeralPublicKey(),
						m.getAcceptTimestamp(), m.getTransportProperties(),
						false);

		// Move to the next state
		IntroducerState state = AWAIT_AUTHS;
		Introducee introducee1, introducee2;
		Contact c;
		if (i.equals(s.getIntroducee1())) {
			if (s.getState() == AWAIT_RESPONSES) state = AWAIT_RESPONSE_A;
			introducee1 = new Introducee(s.getIntroducee1(), sent);
			introducee2 = new Introducee(s.getIntroducee2(), m.getMessageId());
			c = contactManager
					.getContact(txn, s.getIntroducee2().author.getId(),
							identityManager.getLocalAuthor(txn).getId());
		} else if (i.equals(s.getIntroducee2())) {
			if (s.getState() == AWAIT_RESPONSES) state = AWAIT_RESPONSE_B;
			introducee1 = new Introducee(s.getIntroducee1(), m.getMessageId());
			introducee2 = new Introducee(s.getIntroducee2(), sent);
			c = contactManager
					.getContact(txn, s.getIntroducee1().author.getId(),
							identityManager.getLocalAuthor(txn).getId());
		} else throw new AssertionError();

		// Broadcast IntroductionResponseReceivedEvent
		IntroductionResponse request =
				new IntroductionResponse(s.getSessionId(), m.getMessageId(),
						m.getGroupId(), INTRODUCER, m.getTimestamp(), false,
						false, false, false, c.getAuthor().getName(), true);
		IntroductionResponseReceivedEvent e =
				new IntroductionResponseReceivedEvent(c.getId(), request);
		txn.attach(e);

		return new IntroducerSession(s.getSessionId(), state,
				s.getRequestTimestamp(), introducee1, introducee2);
	}

	private IntroducerSession onRemoteDecline(Transaction txn,
			IntroducerSession s, DeclineMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s);

		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());
		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);

		// Forward DECLINE message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		long timestamp = getLocalTimestamp(s, i);
		Message sent = sendDeclineMessage(txn, i, timestamp, false);

		// Move to the START state
		Introducee introducee1, introducee2;
		AuthorId localAuthorId =identityManager.getLocalAuthor(txn).getId();
		Contact c;
		if (i.equals(s.getIntroducee1())) {
			introducee1 = new Introducee(s.getIntroducee1(), sent);
			introducee2 = new Introducee(s.getIntroducee2(), m.getMessageId());
			c = contactManager
					.getContact(txn, s.getIntroducee2().author.getId(),
							localAuthorId);
		} else if (i.equals(s.getIntroducee2())) {
			introducee1 = new Introducee(s.getIntroducee1(), m.getMessageId());
			introducee2 = new Introducee(s.getIntroducee2(), sent);
			c = contactManager
					.getContact(txn, s.getIntroducee1().author.getId(),
							localAuthorId);
		} else throw new AssertionError();

		// Broadcast IntroductionResponseReceivedEvent
		IntroductionResponse request =
				new IntroductionResponse(s.getSessionId(), m.getMessageId(),
						m.getGroupId(), INTRODUCER, m.getTimestamp(), false,
						false, false, false, c.getAuthor().getName(), false);
		IntroductionResponseReceivedEvent e =
				new IntroductionResponseReceivedEvent(c.getId(), request);
		txn.attach(e);

		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introducee1, introducee2);
	}

	private IntroducerSession onRemoteResponseInStart(Transaction txn,
			IntroducerSession s, AbstractIntroductionMessage m)
			throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s);

		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());
		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);

		Introducee i = getIntroducee(s, m.getGroupId());
		Introducee introducee1, introducee2;
		AuthorId localAuthorId = identityManager.getLocalAuthor(txn).getId();
		Contact c;
		if (i.equals(s.getIntroducee1())) {
			introducee1 = new Introducee(s.getIntroducee1(), m.getMessageId());
			introducee2 = s.getIntroducee2();
			c = contactManager
					.getContact(txn, s.getIntroducee1().author.getId(),
							localAuthorId);
		} else if (i.equals(s.getIntroducee2())) {
			introducee1 = s.getIntroducee1();
			introducee2 = new Introducee(s.getIntroducee2(), m.getMessageId());
			c = contactManager
					.getContact(txn, s.getIntroducee2().author.getId(),
							localAuthorId);
		} else throw new AssertionError();

		// Broadcast IntroductionResponseReceivedEvent
		IntroductionResponse request =
				new IntroductionResponse(s.getSessionId(), m.getMessageId(),
						m.getGroupId(), INTRODUCER, m.getTimestamp(), false,
						false, false, false, c.getAuthor().getName(),
						m instanceof AcceptMessage);
		IntroductionResponseReceivedEvent e =
				new IntroductionResponseReceivedEvent(c.getId(), request);
		txn.attach(e);

		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introducee1, introducee2);
	}

	private IntroducerSession onRemoteAuth(Transaction txn,
			IntroducerSession s, AuthMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s);

		// Forward AUTH message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		long timestamp = getLocalTimestamp(s, i);
		Message sent = sendAuthMessage(txn, i, timestamp, m.getMac(),
				m.getSignature());

		// Move to the next state
		IntroducerState state = AWAIT_ACTIVATES;
		Introducee introducee1, introducee2;
		if (i.equals(s.getIntroducee1())) {
			if (s.getState() == AWAIT_AUTHS) state = AWAIT_AUTH_A;
			introducee1 = new Introducee(s.getIntroducee1(), sent);
			introducee2 = new Introducee(s.getIntroducee2(), m.getMessageId());
		} else if (i.equals(s.getIntroducee2())) {
			if (s.getState() == AWAIT_AUTHS) state = AWAIT_AUTH_B;
			introducee1 = new Introducee(s.getIntroducee1(), m.getMessageId());
			introducee2 = new Introducee(s.getIntroducee2(), sent);
		} else throw new AssertionError();
		return new IntroducerSession(s.getSessionId(), state,
				s.getRequestTimestamp(), introducee1, introducee2);
	}

	private IntroducerSession onRemoteActivate(Transaction txn,
			IntroducerSession s, ActivateMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getGroupId(), m.getPreviousMessageId()))
			return abort(txn, s);

		// Forward AUTH message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		long timestamp = getLocalTimestamp(s, i);
		Message sent = sendActivateMessage(txn, i, timestamp);

		// Move to the next state
		IntroducerState state = START;
		Introducee introducee1, introducee2;
		if (i.equals(s.getIntroducee1())) {
			if (s.getState() == AWAIT_ACTIVATES) state = AWAIT_ACTIVATE_A;
			introducee1 = new Introducee(s.getIntroducee1(), sent);
			introducee2 = new Introducee(s.getIntroducee2(), m.getMessageId());
		} else if (i.equals(s.getIntroducee2())) {
			if (s.getState() == AWAIT_ACTIVATES) state = AWAIT_ACTIVATE_B;
			introducee1 = new Introducee(s.getIntroducee1(), m.getMessageId());
			introducee2 = new Introducee(s.getIntroducee2(), sent);
		} else throw new AssertionError();
		return new IntroducerSession(s.getSessionId(), state,
				s.getRequestTimestamp(), introducee1, introducee2);
	}

	private IntroducerSession onRemoteAbort(Transaction txn,
			IntroducerSession s, AbortMessage m) throws DbException {
		// Forward ABORT message
		Introducee i = getOtherIntroducee(s, m.getGroupId());
		long timestamp = getLocalTimestamp(s, i);
		Message sent = sendAbortMessage(txn, i, timestamp);

		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Reset the session back to initial state
		Introducee introducee1, introducee2;
		if (i.equals(s.getIntroducee1())) {
			introducee1 = new Introducee(s.getIntroducee1(), sent);
			introducee2 = new Introducee(s.getIntroducee2(), m.getMessageId());
		} else if (i.equals(s.getIntroducee2())) {
			introducee1 = new Introducee(s.getIntroducee1(), m.getMessageId());
			introducee2 = new Introducee(s.getIntroducee2(), sent);
		} else throw new AssertionError();
		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introducee1, introducee2);
	}

	private IntroducerSession abort(Transaction txn,
			IntroducerSession s) throws DbException {
		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Send an ABORT message to both introducees
		long timestamp1 = getLocalTimestamp(s, s.getIntroducee1());
		Message sent1 = sendAbortMessage(txn, s.getIntroducee1(), timestamp1);
		long timestamp2 = getLocalTimestamp(s, s.getIntroducee2());
		Message sent2 = sendAbortMessage(txn, s.getIntroducee2(), timestamp2);
		// Reset the session back to initial state
		Introducee introducee1 = new Introducee(s.getIntroducee1(), sent1);
		Introducee introducee2 = new Introducee(s.getIntroducee2(), sent2);
		return new IntroducerSession(s.getSessionId(), START,
				s.getRequestTimestamp(), introducee1, introducee2);
	}

	private Introducee getIntroducee(IntroducerSession s, GroupId g) {
		if (s.getIntroducee1().groupId.equals(g)) return s.getIntroducee1();
		else if (s.getIntroducee2().groupId.equals(g))
			return s.getIntroducee2();
		else throw new AssertionError();
	}

	private Introducee getOtherIntroducee(IntroducerSession s, GroupId g) {
		if (s.getIntroducee1().groupId.equals(g)) return s.getIntroducee2();
		else if (s.getIntroducee2().groupId.equals(g))
			return s.getIntroducee1();
		else throw new AssertionError();
	}

	private boolean isInvalidDependency(IntroducerSession session,
			GroupId contactGroupId, @Nullable MessageId dependency) {
		MessageId expected =
				getIntroducee(session, contactGroupId).lastRemoteMessageId;
		return isInvalidDependency(expected, dependency);
	}

	private long getLocalTimestamp(IntroducerSession s, PeerSession p) {
		return getLocalTimestamp(p.getLocalTimestamp(),
				s.getRequestTimestamp());
	}

}
