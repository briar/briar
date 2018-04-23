package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.introduction.event.IntroductionAbortedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionResponseReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionSucceededEvent;

import java.security.GeneralSecurityException;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.introduction.Role.INTRODUCEE;
import static org.briarproject.briar.introduction.IntroduceeState.AWAIT_AUTH;
import static org.briarproject.briar.introduction.IntroduceeState.AWAIT_RESPONSES;
import static org.briarproject.briar.introduction.IntroduceeState.LOCAL_ACCEPTED;
import static org.briarproject.briar.introduction.IntroduceeState.REMOTE_ACCEPTED;

@Immutable
@NotNullByDefault
class IntroduceeProtocolEngine
		extends AbstractProtocolEngine<IntroduceeSession> {

	private final IntroductionCrypto crypto;
	private final KeyManager keyManager;
	private final TransportPropertyManager transportPropertyManager;

	@Inject
	IntroduceeProtocolEngine(
			DatabaseComponent db,
			ClientHelper clientHelper,
			ContactManager contactManager,
			ContactGroupFactory contactGroupFactory,
			MessageTracker messageTracker,
			IdentityManager identityManager,
			MessageParser messageParser,
			MessageEncoder messageEncoder,
			Clock clock,
			IntroductionCrypto crypto,
			KeyManager keyManager,
			TransportPropertyManager transportPropertyManager) {
		super(db, clientHelper, contactManager, contactGroupFactory,
				messageTracker, identityManager, messageParser, messageEncoder,
				clock);
		this.crypto = crypto;
		this.keyManager = keyManager;
		this.transportPropertyManager = transportPropertyManager;
	}

	@Override
	public IntroduceeSession onRequestAction(Transaction txn,
			IntroduceeSession session, @Nullable String message,
			long timestamp) {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	@Override
	public IntroduceeSession onAcceptAction(Transaction txn,
			IntroduceeSession session, long timestamp) throws DbException {
		switch (session.getState()) {
			case AWAIT_RESPONSES:
			case REMOTE_ACCEPTED:
				return onLocalAccept(txn, session, timestamp);
			case START:
			case LOCAL_DECLINED:
			case LOCAL_ACCEPTED:
			case AWAIT_AUTH:
			case AWAIT_ACTIVATE:
				throw new ProtocolStateException(); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onDeclineAction(Transaction txn,
			IntroduceeSession session, long timestamp) throws DbException {
		switch (session.getState()) {
			case AWAIT_RESPONSES:
			case REMOTE_ACCEPTED:
				return onLocalDecline(txn, session, timestamp);
			case START:
			case LOCAL_DECLINED:
			case LOCAL_ACCEPTED:
			case AWAIT_AUTH:
			case AWAIT_ACTIVATE:
				throw new ProtocolStateException(); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onRequestMessage(Transaction txn,
			IntroduceeSession session, RequestMessage m)
			throws DbException, FormatException {
		switch (session.getState()) {
			case START:
				return onRemoteRequest(txn, session, m);
			case AWAIT_RESPONSES:
			case LOCAL_DECLINED:
			case LOCAL_ACCEPTED:
			case REMOTE_ACCEPTED:
			case AWAIT_AUTH:
			case AWAIT_ACTIVATE:
				return abort(txn, session); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onAcceptMessage(Transaction txn,
			IntroduceeSession session, AcceptMessage m)
			throws DbException, FormatException {
		switch (session.getState()) {
			case START:
				return onRemoteResponseInStart(txn, session, m);
			case AWAIT_RESPONSES:
			case LOCAL_ACCEPTED:
				return onRemoteAccept(txn, session, m);
			case LOCAL_DECLINED:
			case REMOTE_ACCEPTED:
			case AWAIT_AUTH:
			case AWAIT_ACTIVATE:
				return abort(txn, session); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onDeclineMessage(Transaction txn,
			IntroduceeSession session, DeclineMessage m)
			throws DbException, FormatException {
		switch (session.getState()) {
			case START:
				return onRemoteResponseInStart(txn, session, m);
			case AWAIT_RESPONSES:
			case LOCAL_DECLINED:
			case LOCAL_ACCEPTED:
				return onRemoteDecline(txn, session, m);
			case REMOTE_ACCEPTED:
			case AWAIT_AUTH:
			case AWAIT_ACTIVATE:
				return abort(txn, session); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onAuthMessage(Transaction txn,
			IntroduceeSession session, AuthMessage m)
			throws DbException, FormatException {
		switch (session.getState()) {
			case AWAIT_AUTH:
				return onRemoteAuth(txn, session, m);
			case START:
			case AWAIT_RESPONSES:
			case LOCAL_DECLINED:
			case LOCAL_ACCEPTED:
			case REMOTE_ACCEPTED:
			case AWAIT_ACTIVATE:
				return abort(txn, session); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onActivateMessage(Transaction txn,
			IntroduceeSession session, ActivateMessage m)
			throws DbException, FormatException {
		switch (session.getState()) {
			case AWAIT_ACTIVATE:
				return onRemoteActivate(txn, session, m);
			case START:
			case AWAIT_RESPONSES:
			case LOCAL_DECLINED:
			case LOCAL_ACCEPTED:
			case REMOTE_ACCEPTED:
			case AWAIT_AUTH:
				return abort(txn, session); // Invalid in these states
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onAbortMessage(Transaction txn,
			IntroduceeSession session, AbortMessage m)
			throws DbException, FormatException {
		return onRemoteAbort(txn, session, m);
	}

	private IntroduceeSession onRemoteRequest(Transaction txn,
			IntroduceeSession s, RequestMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);

		// Mark the request visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());

		// Add SessionId to message metadata
		addSessionId(txn, m.getMessageId(), s.getSessionId());

		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);

		// Broadcast IntroductionRequestReceivedEvent
		Contact c = contactManager.getContact(txn, s.getIntroducer().getId(),
				identityManager.getLocalAuthor(txn).getId());
		boolean contactExists = false; // TODO
		IntroductionRequest request =
				new IntroductionRequest(s.getSessionId(), m.getMessageId(),
						m.getGroupId(), INTRODUCEE, m.getTimestamp(), false,
						false, false, false, m.getAuthor().getName(), false,
						m.getMessage(), false, contactExists);
		IntroductionRequestReceivedEvent e =
				new IntroductionRequestReceivedEvent(c.getId(), request);
		txn.attach(e);

		// Move to the AWAIT_RESPONSES state
		return IntroduceeSession.addRemoteRequest(s, AWAIT_RESPONSES, m);
	}

	private IntroduceeSession onLocalAccept(Transaction txn,
			IntroduceeSession s, long timestamp) throws DbException {
		// Mark the request message unavailable to answer
		MessageId requestId = s.getLastRemoteMessageId();
		if (requestId == null) throw new IllegalStateException();
		markRequestUnavailableToAnswer(txn, requestId);

		// Create ephemeral key pair and get local transport properties
		KeyPair keyPair = crypto.generateKeyPair();
		byte[] publicKey = keyPair.getPublic().getEncoded();
		byte[] privateKey = keyPair.getPrivate().getEncoded();
		Map<TransportId, TransportProperties> transportProperties =
				transportPropertyManager.getLocalProperties(txn);

		// Send a ACCEPT message
		long localTimestamp =
				Math.max(timestamp, getLocalTimestamp(s));
		Message sent = sendAcceptMessage(txn, s, localTimestamp, publicKey,
				localTimestamp, transportProperties, true);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);

		// Determine the next state
		IntroduceeState state =
				s.getState() == AWAIT_RESPONSES ? LOCAL_ACCEPTED : AWAIT_AUTH;
		IntroduceeSession sNew = IntroduceeSession
				.addLocalAccept(s, state, sent, publicKey, privateKey,
						localTimestamp, transportProperties);

		if (state == AWAIT_AUTH) {
			// Move to the AWAIT_AUTH state
			return onLocalAuth(txn, sNew);
		}
		// Move to the LOCAL_ACCEPTED state
		return sNew;
	}

	private IntroduceeSession onLocalDecline(Transaction txn,
			IntroduceeSession s, long timestamp) throws DbException {
		// Mark the request message unavailable to answer
		MessageId requestId = s.getLastRemoteMessageId();
		if (requestId == null) throw new IllegalStateException();
		markRequestUnavailableToAnswer(txn, requestId);

		// Send a DECLINE message
		long localTimestamp = Math.max(timestamp, getLocalTimestamp(s));
		Message sent = sendDeclineMessage(txn, s, localTimestamp, true);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);

		// Move to the START state
		return IntroduceeSession.clear(s, sent.getId(), sent.getTimestamp(),
				s.getLastRemoteMessageId());
	}

	private IntroduceeSession onRemoteAccept(Transaction txn,
			IntroduceeSession s, AcceptMessage m)
			throws DbException, FormatException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);

		// Determine next state
		IntroduceeState state =
				s.getState() == AWAIT_RESPONSES ? REMOTE_ACCEPTED : AWAIT_AUTH;

		if (state == AWAIT_AUTH) {
			// Move to the AWAIT_AUTH state and send own auth message
			return onLocalAuth(txn,
					IntroduceeSession.addRemoteAccept(s, AWAIT_AUTH, m));
		}
		// Move to the REMOTE_ACCEPTED state
		return IntroduceeSession.addRemoteAccept(s, state, m);
	}

	private IntroduceeSession onRemoteDecline(Transaction txn,
			IntroduceeSession s, DeclineMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);

		// Mark the request visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());

		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);

		// Broadcast IntroductionResponseReceivedEvent
		Contact c = contactManager.getContact(txn, s.getIntroducer().getId(),
				identityManager.getLocalAuthor(txn).getId());
		IntroductionResponse request =
				new IntroductionResponse(s.getSessionId(), m.getMessageId(),
						m.getGroupId(), INTRODUCEE, m.getTimestamp(), false,
						false, false, false, s.getRemoteAuthor().getName(),
						false);
		IntroductionResponseReceivedEvent e =
				new IntroductionResponseReceivedEvent(c.getId(), request);
		txn.attach(e);

		// Move back to START state
		return IntroduceeSession
				.clear(s, s.getLastLocalMessageId(), s.getLocalTimestamp(),
						m.getMessageId());
	}

	private IntroduceeSession onRemoteResponseInStart(Transaction txn,
			IntroduceeSession s, AbstractIntroductionMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s);
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);

		// Stay in START state
		return IntroduceeSession
				.clear(s, s.getLastLocalMessageId(), s.getLocalTimestamp(),
						m.getMessageId());
	}

	private IntroduceeSession onLocalAuth(Transaction txn, IntroduceeSession s)
			throws DbException {
		boolean alice = isAlice(txn, s);
		byte[] mac;
		byte[] signature;
		SecretKey masterKey;
		try {
			masterKey = crypto.deriveMasterKey(s, alice);
			SecretKey macKey = crypto.deriveMacKey(masterKey, alice);
			LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
			mac = crypto.mac(macKey, s, localAuthor.getId(), alice);
			signature = crypto.sign(macKey, localAuthor.getPrivateKey());
		} catch (GeneralSecurityException e) {
			// TODO
			return abort(txn, s);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
		if (s.getState() != AWAIT_AUTH) throw new AssertionError();
		Message sent = sendAuthMessage(txn, s, getLocalTimestamp(s), mac,
				signature);
		return IntroduceeSession.addLocalAuth(s, AWAIT_AUTH, masterKey, sent);
	}

	private IntroduceeSession onRemoteAuth(Transaction txn,
			IntroduceeSession s, AuthMessage m)
			throws DbException, FormatException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);

		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		try {
			crypto.verifyMac(m.getMac(), s, localAuthor.getId());
			crypto.verifySignature(m.getSignature(), s, localAuthor.getId());
		} catch (GeneralSecurityException e) {
			return abort(txn, s);
		}

		try {
			ContactId c = contactManager
					.addContact(txn, s.getRemoteAuthor(), localAuthor.getId(),
							false, false);
			//noinspection ConstantConditions
			transportPropertyManager.addRemoteProperties(txn, c,
					s.getRemoteTransportProperties());
		} catch (ContactExistsException e) {
			// TODO
		}

		long timestamp =
				Math.min(s.getAcceptTimestamp(), s.getRemoteAcceptTimestamp());
		if (timestamp == -1) throw new AssertionError();

		//noinspection ConstantConditions
		Map<TransportId, KeySetId> keys = keyManager
				.addUnboundKeys(txn, new SecretKey(s.getMasterKey()), timestamp,
						isAlice(txn, s));

		Message sent = sendActivateMessage(txn, s, getLocalTimestamp(s));

		// Move to AWAIT_ACTIVATE state and clear key material from session
		return IntroduceeSession.awaitActivate(s, m, sent, keys);
	}

	private IntroduceeSession onRemoteActivate(Transaction txn,
			IntroduceeSession s, ActivateMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s);

		Contact c = contactManager.getContact(txn, s.getRemoteAuthor().getId(),
				identityManager.getLocalAuthor(txn).getId());
		keyManager.bindKeys(txn, c.getId(), s.getTransportKeys());
		keyManager.activateKeys(txn, s.getTransportKeys());

		// TODO remove when concept of inactive contacts is removed
		contactManager.setContactActive(txn, c.getId(), true);

		// Broadcast IntroductionSucceededEvent
		IntroductionSucceededEvent e = new IntroductionSucceededEvent(c);
		txn.attach(e);

		// Move back to START state
		return IntroduceeSession
				.clear(s, s.getLastLocalMessageId(), s.getLocalTimestamp(),
						m.getMessageId());
	}

	private IntroduceeSession onRemoteAbort(Transaction txn,
			IntroduceeSession s, AbortMessage m)
			throws DbException {
		// Mark the request message unavailable to answer
		MessageId requestId = s.getLastRemoteMessageId();
		if (requestId == null) throw new IllegalStateException();
		markRequestUnavailableToAnswer(txn, requestId);

		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Reset the session back to initial state
		return IntroduceeSession
				.clear(s, s.getLastLocalMessageId(), s.getLocalTimestamp(),
						m.getMessageId());
	}

	private IntroduceeSession abort(Transaction txn, IntroduceeSession s)
			throws DbException {
		// Mark the request message unavailable to answer
		MessageId requestId = s.getLastRemoteMessageId();
		if (requestId == null) throw new IllegalStateException();
		markRequestUnavailableToAnswer(txn, requestId);

		// Send an ABORT message
		Message sent = sendAbortMessage(txn, s, getLocalTimestamp(s));

		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Reset the session back to initial state
		return IntroduceeSession.clear(s, sent.getId(), sent.getTimestamp(),
				s.getLastRemoteMessageId());
	}

	private boolean isInvalidDependency(IntroduceeSession s,
			@Nullable MessageId dependency) {
		return isInvalidDependency(s.getLastRemoteMessageId(), dependency);
	}

	private long getLocalTimestamp(IntroduceeSession s) {
		return getLocalTimestamp(s.getLocalTimestamp(),
				s.getRequestTimestamp());
	}

	private boolean isAlice(Transaction txn, IntroduceeSession s)
			throws DbException {
		Author localAuthor = identityManager.getLocalAuthor(txn);
		return crypto.isAlice(localAuthor.getId(), s.getRemoteAuthor().getId());
	}

	private void addSessionId(Transaction txn, MessageId m, SessionId sessionId)
			throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.addSessionId(meta, sessionId);
		try {
			clientHelper.mergeMessageMetadata(txn, m, meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

}
