package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
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
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.event.IntroductionAbortedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.lang.Math.max;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.system.Clock.MIN_REASONABLE_TIME_MS;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.introduction.IntroduceeState.AWAIT_AUTH;
import static org.briarproject.briar.introduction.IntroduceeState.AWAIT_RESPONSES;
import static org.briarproject.briar.introduction.IntroduceeState.LOCAL_ACCEPTED;
import static org.briarproject.briar.introduction.IntroduceeState.LOCAL_DECLINED;
import static org.briarproject.briar.introduction.IntroduceeState.REMOTE_ACCEPTED;
import static org.briarproject.briar.introduction.IntroduceeState.REMOTE_DECLINED;
import static org.briarproject.briar.introduction.IntroduceeState.START;

@Immutable
@NotNullByDefault
class IntroduceeProtocolEngine
		extends AbstractProtocolEngine<IntroduceeSession> {

	private final static Logger LOG =
			Logger.getLogger(IntroduceeProtocolEngine.class.getName());

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
			AuthorManager authorManager,
			MessageParser messageParser,
			MessageEncoder messageEncoder,
			IntroductionCrypto crypto,
			KeyManager keyManager,
			TransportPropertyManager transportPropertyManager,
			ClientVersioningManager clientVersioningManager,
			AutoDeleteManager autoDeleteManager,
			ConversationManager conversationManager,
			Clock clock) {
		super(db, clientHelper, contactManager, contactGroupFactory,
				messageTracker, identityManager, authorManager, messageParser,
				messageEncoder, clientVersioningManager, autoDeleteManager,
				conversationManager, clock);
		this.crypto = crypto;
		this.keyManager = keyManager;
		this.transportPropertyManager = transportPropertyManager;
	}

	@Override
	public IntroduceeSession onRequestAction(Transaction txn,
			IntroduceeSession session, @Nullable String text) {
		throw new UnsupportedOperationException(); // Invalid in this role
	}

	@Override
	public IntroduceeSession onAcceptAction(Transaction txn,
			IntroduceeSession session) throws DbException {
		switch (session.getState()) {
			case AWAIT_RESPONSES:
			case REMOTE_DECLINED:
			case REMOTE_ACCEPTED:
				return onLocalAccept(txn, session);
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
			IntroduceeSession session, boolean isAutoDecline)
			throws DbException {
		switch (session.getState()) {
			case AWAIT_RESPONSES:
			case REMOTE_DECLINED:
			case REMOTE_ACCEPTED:
				return onLocalDecline(txn, session, isAutoDecline);
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
			IntroduceeSession session, RequestMessage m) throws DbException {
		switch (session.getState()) {
			case START:
				return onRemoteRequest(txn, session, m);
			case AWAIT_RESPONSES:
			case LOCAL_DECLINED:
			case REMOTE_DECLINED:
			case LOCAL_ACCEPTED:
			case REMOTE_ACCEPTED:
			case AWAIT_AUTH:
			case AWAIT_ACTIVATE:
				// Invalid in these states
				return abort(txn, session, m.getMessageId());
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onAcceptMessage(Transaction txn,
			IntroduceeSession session, AcceptMessage m) throws DbException {
		switch (session.getState()) {
			case LOCAL_DECLINED:
				return onRemoteResponseWhenDeclined(txn, session, m);
			case AWAIT_RESPONSES:
			case LOCAL_ACCEPTED:
				return onRemoteAccept(txn, session, m);
			case START:
			case REMOTE_DECLINED:
			case REMOTE_ACCEPTED:
			case AWAIT_AUTH:
			case AWAIT_ACTIVATE:
				// Invalid in these states
				return abort(txn, session, m.getMessageId());
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onDeclineMessage(Transaction txn,
			IntroduceeSession session, DeclineMessage m) throws DbException {
		switch (session.getState()) {
			case LOCAL_DECLINED:
				return onRemoteResponseWhenDeclined(txn, session, m);
			case AWAIT_RESPONSES:
			case LOCAL_ACCEPTED:
				return onRemoteDecline(txn, session, m);
			case START:
			case REMOTE_DECLINED:
			case REMOTE_ACCEPTED:
			case AWAIT_AUTH:
			case AWAIT_ACTIVATE:
				// Invalid in these states
				return abort(txn, session, m.getMessageId());
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onAuthMessage(Transaction txn,
			IntroduceeSession session, AuthMessage m) throws DbException {
		switch (session.getState()) {
			case AWAIT_AUTH:
				return onRemoteAuth(txn, session, m);
			case START:
			case AWAIT_RESPONSES:
			case LOCAL_DECLINED:
			case REMOTE_DECLINED:
			case LOCAL_ACCEPTED:
			case REMOTE_ACCEPTED:
			case AWAIT_ACTIVATE:
				// Invalid in these states
				return abort(txn, session, m.getMessageId());
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onActivateMessage(Transaction txn,
			IntroduceeSession session, ActivateMessage m) throws DbException {
		switch (session.getState()) {
			case AWAIT_ACTIVATE:
				return onRemoteActivate(txn, session, m);
			case START:
			case AWAIT_RESPONSES:
			case LOCAL_DECLINED:
			case REMOTE_DECLINED:
			case LOCAL_ACCEPTED:
			case REMOTE_ACCEPTED:
			case AWAIT_AUTH:
				// Invalid in these states
				return abort(txn, session, m.getMessageId());
			default:
				throw new AssertionError();
		}
	}

	@Override
	public IntroduceeSession onAbortMessage(Transaction txn,
			IntroduceeSession session, AbortMessage m) throws DbException {
		return onRemoteAbort(txn, session, m);
	}

	private IntroduceeSession onRemoteRequest(Transaction txn,
			IntroduceeSession s, RequestMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s, m.getMessageId());

		// Mark the request visible in the UI and available to answer
		markMessageVisibleInUi(txn, m.getMessageId());
		markRequestAvailableToAnswer(txn, m.getMessageId(), true);

		// Add SessionId to message metadata
		addSessionId(txn, m.getMessageId(), s.getSessionId());

		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);

		// Receive the auto-delete timer
		receiveAutoDeleteTimer(txn, m);

		// Broadcast IntroductionRequestReceivedEvent
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		Contact c = contactManager.getContact(txn, s.getIntroducer().getId(),
				localAuthor.getId());
		AuthorInfo authorInfo =
				authorManager.getAuthorInfo(txn, m.getAuthor().getId());
		IntroductionRequest request = new IntroductionRequest(m.getMessageId(),
				m.getGroupId(), m.getTimestamp(), false, false, false, false,
				s.getSessionId(), m.getAuthor(), m.getText(), false,
				authorInfo, m.getAutoDeleteTimer());
		IntroductionRequestReceivedEvent e =
				new IntroductionRequestReceivedEvent(request, c.getId());
		txn.attach(e);

		// Move to the AWAIT_RESPONSES state
		return IntroduceeSession.addRemoteRequest(s, AWAIT_RESPONSES, m);
	}

	private IntroduceeSession onLocalAccept(Transaction txn,
			IntroduceeSession s) throws DbException {
		// Mark the request message unavailable to answer
		markRequestsUnavailableToAnswer(txn, s);

		// Create ephemeral key pair and get local transport properties
		KeyPair keyPair = crypto.generateAgreementKeyPair();
		PublicKey publicKey = keyPair.getPublic();
		PrivateKey privateKey = keyPair.getPrivate();
		Map<TransportId, TransportProperties> transportProperties =
				transportPropertyManager.getLocalProperties(txn);

		// Send an ACCEPT message
		long localTimestamp = getTimestampForVisibleMessage(txn, s);
		Message sent = sendAcceptMessage(txn, s, localTimestamp, publicKey,
				localTimestamp, transportProperties, true);
		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);

		// Determine the next state
		switch (s.getState()) {
			case AWAIT_RESPONSES:
				return IntroduceeSession.addLocalAccept(s, LOCAL_ACCEPTED, sent,
						publicKey, privateKey, localTimestamp,
						transportProperties);
			case REMOTE_DECLINED:
				return IntroduceeSession.clear(s, START, sent.getId(),
						localTimestamp, s.getLastRemoteMessageId());
			case REMOTE_ACCEPTED:
				return onLocalAuth(txn, IntroduceeSession.addLocalAccept(s,
						AWAIT_AUTH, sent, publicKey, privateKey, localTimestamp,
						transportProperties));
			default:
				throw new AssertionError();
		}
	}

	private IntroduceeSession onLocalDecline(Transaction txn,
			IntroduceeSession s, boolean isAutoDecline) throws DbException {
		// Mark the request message unavailable to answer
		markRequestsUnavailableToAnswer(txn, s);

		// Send a DECLINE message
		long localTimestamp = getTimestampForVisibleMessage(txn, s);
		Message sent =
				sendDeclineMessage(txn, s, localTimestamp, true, isAutoDecline);

		// Track the message
		messageTracker.trackOutgoingMessage(txn, sent);

		// Move to the START or LOCAL_DECLINED state, if still awaiting response
		IntroduceeState state =
				s.getState() == AWAIT_RESPONSES ? LOCAL_DECLINED : START;
		return IntroduceeSession.clear(s, state, sent.getId(),
				sent.getTimestamp(), s.getLastRemoteMessageId());
	}

	private IntroduceeSession onRemoteAccept(Transaction txn,
			IntroduceeSession s, AcceptMessage m) throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s, m.getMessageId());
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s, m.getMessageId());

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
			return abort(txn, s, m.getMessageId());
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s, m.getMessageId());

		// Mark the response visible in the UI
		markMessageVisibleInUi(txn, m.getMessageId());

		// Track the incoming message
		messageTracker
				.trackMessage(txn, m.getGroupId(), m.getTimestamp(), false);

		// Receive the auto-delete timer
		receiveAutoDeleteTimer(txn, m);

		// Broadcast IntroductionResponseReceivedEvent
		broadcastIntroductionResponseReceivedEvent(txn, s,
				s.getIntroducer().getId(), s.getRemote().author, m, false);

		// Determine next state
		IntroduceeState state =
				s.getState() == AWAIT_RESPONSES ? REMOTE_DECLINED : START;

		// Move to the next state
		return IntroduceeSession.clear(s, state, s.getLastLocalMessageId(),
				s.getLocalTimestamp(), m.getMessageId());
	}

	private IntroduceeSession onRemoteResponseWhenDeclined(Transaction txn,
			IntroduceeSession s, AbstractIntroductionMessage m)
			throws DbException {
		// The timestamp must be higher than the last request message
		if (m.getTimestamp() <= s.getRequestTimestamp())
			return abort(txn, s, m.getMessageId());
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s, m.getMessageId());

		// Move to START state
		return IntroduceeSession.clear(s, START, s.getLastLocalMessageId(),
				s.getLocalTimestamp(), m.getMessageId());
	}

	private IntroduceeSession onLocalAuth(Transaction txn, IntroduceeSession s)
			throws DbException {
		byte[] mac;
		byte[] signature;
		SecretKey masterKey, aliceMacKey, bobMacKey;
		try {
			masterKey = crypto.deriveMasterKey(s);
			aliceMacKey = crypto.deriveMacKey(masterKey, true);
			bobMacKey = crypto.deriveMacKey(masterKey, false);
			SecretKey ourMacKey = s.getLocal().alice ? aliceMacKey : bobMacKey;
			LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
			mac = crypto.authMac(ourMacKey, s, localAuthor.getId());
			signature = crypto.sign(ourMacKey, localAuthor.getPrivateKey());
		} catch (GeneralSecurityException e) {
			logException(LOG, WARNING, e);
			return abort(txn, s, s.getLastRemoteMessageId());
		}
		if (s.getState() != AWAIT_AUTH) throw new AssertionError();
		long localTimestamp = getTimestampForInvisibleMessage(s);
		Message sent = sendAuthMessage(txn, s, localTimestamp, mac, signature);
		return IntroduceeSession.addLocalAuth(s, AWAIT_AUTH, sent, masterKey,
				aliceMacKey, bobMacKey);
	}

	private IntroduceeSession onRemoteAuth(Transaction txn,
			IntroduceeSession s, AuthMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s, m.getMessageId());

		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		try {
			crypto.verifyAuthMac(m.getMac(), s, localAuthor.getId());
			crypto.verifySignature(m.getSignature(), s);
		} catch (GeneralSecurityException e) {
			return abort(txn, s, m.getMessageId());
		}
		long timestamp = Math.min(s.getLocal().acceptTimestamp,
				s.getRemote().acceptTimestamp);
		if (timestamp == -1) throw new AssertionError();
		if (timestamp < MIN_REASONABLE_TIME_MS) {
			LOG.warning("Timestamp is too old");
			return abort(txn, s, m.getMessageId());
		}

		Map<TransportId, KeySetId> keys = null;
		try {
			ContactId contactId = contactManager.addContact(txn,
					s.getRemote().author, localAuthor.getId(), false);

			// Only add transport properties and keys when the contact was added
			// This will be changed once we have a way to reset state for peers
			// that were contacts already at some point in the past.

			// add the keys to the new contact
			keys = keyManager.addRotationKeys(txn, contactId,
					new SecretKey(s.getMasterKey()), timestamp,
					s.getLocal().alice, false);

			// add signed transport properties for the contact
			transportPropertyManager.addRemoteProperties(txn, contactId,
					requireNonNull(s.getRemote().transportProperties));
		} catch (ContactExistsException e) {
			// Ignore this, because the other introducee might have deleted us
		}

		// send ACTIVATE message with a MAC
		byte[] mac = crypto.activateMac(s);
		long localTimestamp = getTimestampForInvisibleMessage(s);
		Message sent = sendActivateMessage(txn, s, localTimestamp, mac);

		// Move to AWAIT_ACTIVATE state and clear key material from session
		return IntroduceeSession.awaitActivate(s, m, sent, keys);
	}

	private IntroduceeSession onRemoteActivate(Transaction txn,
			IntroduceeSession s, ActivateMessage m) throws DbException {
		// The dependency, if any, must be the last remote message
		if (isInvalidDependency(s, m.getPreviousMessageId()))
			return abort(txn, s, m.getMessageId());

		// Validate MAC
		try {
			crypto.verifyActivateMac(m.getMac(), s);
		} catch (GeneralSecurityException e) {
			return abort(txn, s, m.getMessageId());
		}

		// We might not have added transport keys
		// if the contact existed when the remote AUTH was received.
		if (s.getTransportKeys() != null) {
			// Activate transport keys
			keyManager.activateKeys(txn, s.getTransportKeys());
		}

		// Move back to START state
		return IntroduceeSession.clear(s, START, s.getLastLocalMessageId(),
				s.getLocalTimestamp(), m.getMessageId());
	}

	private IntroduceeSession onRemoteAbort(Transaction txn,
			IntroduceeSession s, AbortMessage m)
			throws DbException {
		// Mark the request message unavailable to answer
		markRequestsUnavailableToAnswer(txn, s);

		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Reset the session back to initial state
		return IntroduceeSession.clear(s, START, s.getLastLocalMessageId(),
				s.getLocalTimestamp(), m.getMessageId());
	}

	private IntroduceeSession abort(Transaction txn, IntroduceeSession s,
			@Nullable MessageId lastRemoteMessageId) throws DbException {
		// Mark the request message unavailable to answer
		markRequestsUnavailableToAnswer(txn, s);

		// Send an ABORT message
		long localTimestamp = getTimestampForInvisibleMessage(s);
		Message sent = sendAbortMessage(txn, s, localTimestamp);

		// Broadcast abort event for testing
		txn.attach(new IntroductionAbortedEvent(s.getSessionId()));

		// Reset the session back to initial state
		return IntroduceeSession.clear(s, START, sent.getId(),
				sent.getTimestamp(), lastRemoteMessageId);
	}

	private boolean isInvalidDependency(IntroduceeSession s,
			@Nullable MessageId dependency) {
		return isInvalidDependency(s.getLastRemoteMessageId(), dependency);
	}

	/**
	 * Returns a timestamp for a visible outgoing message. The timestamp is
	 * later than the timestamp of any message sent or received so far in the
	 * conversation, and later than the {@link
	 * #getSessionTimestamp(IntroduceeSession) session timestamp}.
	 */
	private long getTimestampForVisibleMessage(Transaction txn,
			IntroduceeSession s) throws DbException {
		long conversationTimestamp =
				getTimestampForOutgoingMessage(txn, s.getContactGroupId());
		return max(conversationTimestamp, getSessionTimestamp(s) + 1);
	}

	/**
	 * Returns a timestamp for an invisible outgoing message. The timestamp is
	 * later than the {@link #getSessionTimestamp(IntroduceeSession) session
	 * timestamp}.
	 */
	private long getTimestampForInvisibleMessage(IntroduceeSession s) {
		return max(clock.currentTimeMillis(), getSessionTimestamp(s) + 1);
	}

	/**
	 * Returns the latest timestamp of any message sent so far in the session,
	 * and any request message received so far in the session.
	 */
	private long getSessionTimestamp(IntroduceeSession s) {
		return max(s.getLocalTimestamp(), s.getRequestTimestamp());
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

	private void markRequestsUnavailableToAnswer(Transaction txn,
			IntroduceeSession s) throws DbException {
		BdfDictionary query = messageParser
				.getRequestsAvailableToAnswerQuery(s.getSessionId());
		try {
			Collection<MessageId> results = clientHelper.getMessageIds(txn,
					s.getContactGroupId(), query);
			for (MessageId m : results)
				markRequestAvailableToAnswer(txn, m, false);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private void markRequestAvailableToAnswer(Transaction txn, MessageId m,
			boolean available) throws DbException {
		BdfDictionary meta = new BdfDictionary();
		messageEncoder.setAvailableToAnswer(meta, available);
		try {
			clientHelper.mergeMessageMetadata(txn, m, meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

}
