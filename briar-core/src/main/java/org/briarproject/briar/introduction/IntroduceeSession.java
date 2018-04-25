package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.Role;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.introduction.IntroduceeState.AWAIT_ACTIVATE;
import static org.briarproject.briar.introduction.IntroduceeState.START;
import static org.briarproject.briar.api.introduction.Role.INTRODUCEE;

@Immutable
@NotNullByDefault
class IntroduceeSession extends Session<IntroduceeState>
		implements PeerSession {

	private final GroupId contactGroupId;
	private final long localTimestamp, acceptTimestamp, remoteAcceptTimestamp;
	@Nullable
	private final MessageId lastLocalMessageId, lastRemoteMessageId;
	private final Author introducer, remoteAuthor;
	@Nullable
	private final byte[] ephemeralPublicKey, ephemeralPrivateKey;
	@Nullable
	private final byte[] masterKey, remoteEphemeralPublicKey;
	@Nullable
	private final Map<TransportId, TransportProperties> transportProperties;
	@Nullable
	private final Map<TransportId, TransportProperties>
			remoteTransportProperties;
	@Nullable
	private final Map<TransportId, KeySetId> transportKeys;

	IntroduceeSession(SessionId sessionId, IntroduceeState state,
			long requestTimestamp, GroupId contactGroupId,
			@Nullable MessageId lastLocalMessageId, long localTimestamp,
			@Nullable MessageId lastRemoteMessageId, Author introducer,
			@Nullable byte[] ephemeralPublicKey,
			@Nullable byte[] ephemeralPrivateKey,
			@Nullable Map<TransportId, TransportProperties> transportProperties,
			long acceptTimestamp, @Nullable byte[] masterKey,
			Author remoteAuthor,
			@Nullable byte[] remoteEphemeralPublicKey, @Nullable
			Map<TransportId, TransportProperties> remoteTransportProperties,
			long remoteAcceptTimestamp,
			@Nullable Map<TransportId, KeySetId> transportKeys) {
		super(sessionId, state, requestTimestamp);
		this.contactGroupId = contactGroupId;
		this.lastLocalMessageId = lastLocalMessageId;
		this.localTimestamp = localTimestamp;
		this.lastRemoteMessageId = lastRemoteMessageId;
		this.introducer = introducer;
		this.ephemeralPublicKey = ephemeralPublicKey;
		this.ephemeralPrivateKey = ephemeralPrivateKey;
		this.transportProperties = transportProperties;
		this.acceptTimestamp = acceptTimestamp;
		this.masterKey = masterKey;
		this.remoteAuthor = remoteAuthor;
		this.remoteEphemeralPublicKey = remoteEphemeralPublicKey;
		this.remoteTransportProperties = remoteTransportProperties;
		this.remoteAcceptTimestamp = remoteAcceptTimestamp;
		this.transportKeys = transportKeys;
	}

	static IntroduceeSession getInitial(GroupId contactGroupId,
			SessionId sessionId, Author introducer, Author remoteAuthor) {
		return new IntroduceeSession(sessionId, START, -1, contactGroupId, null,
				-1, null, introducer, null, null, null, -1, null, remoteAuthor,
				null, null, -1, null);
	}

	static IntroduceeSession addRemoteRequest(IntroduceeSession s,
			IntroduceeState state, RequestMessage m) {
		return new IntroduceeSession(s.getSessionId(), state, m.getTimestamp(),
				s.contactGroupId, s.lastLocalMessageId, s.localTimestamp,
				m.getMessageId(), s.introducer, s.ephemeralPublicKey,
				s.ephemeralPrivateKey, s.transportProperties, s.acceptTimestamp,
				s.masterKey, s.remoteAuthor, s.remoteEphemeralPublicKey,
				s.remoteTransportProperties, s.remoteAcceptTimestamp,
				s.transportKeys);
	}

	static IntroduceeSession addLocalAccept(IntroduceeSession s,
			IntroduceeState state, Message acceptMessage,
			byte[] ephemeralPublicKey, byte[] ephemeralPrivateKey,
			long acceptTimestamp,
			Map<TransportId, TransportProperties> transportProperties) {
		return new IntroduceeSession(s.getSessionId(), state,
				s.getRequestTimestamp(), s.contactGroupId,
				acceptMessage.getId(), acceptMessage.getTimestamp(),
				s.lastRemoteMessageId, s.introducer, ephemeralPublicKey,
				ephemeralPrivateKey, transportProperties,
				acceptTimestamp, s.masterKey, s.remoteAuthor,
				s.remoteEphemeralPublicKey, s.remoteTransportProperties,
				s.remoteAcceptTimestamp, s.transportKeys);
	}

	static IntroduceeSession addRemoteAccept(IntroduceeSession s,
			IntroduceeState state, AcceptMessage acceptMessage) {
		return new IntroduceeSession(s.getSessionId(), state,
				s.getRequestTimestamp(), s.contactGroupId, s.lastLocalMessageId,
				s.localTimestamp, acceptMessage.getMessageId(), s.introducer,
				s.ephemeralPublicKey, s.ephemeralPrivateKey,
				s.transportProperties, s.acceptTimestamp, s.masterKey,
				s.remoteAuthor, acceptMessage.getEphemeralPublicKey(),
				acceptMessage.getTransportProperties(),
				acceptMessage.getAcceptTimestamp(), s.transportKeys);
	}

	static IntroduceeSession addLocalAuth(IntroduceeSession s,
			IntroduceeState state, SecretKey masterKey, Message m) {
		return new IntroduceeSession(s.getSessionId(), state,
				s.getRequestTimestamp(), s.contactGroupId, m.getId(),
				m.getTimestamp(), s.lastRemoteMessageId, s.introducer,
				s.ephemeralPublicKey, s.ephemeralPrivateKey,
				s.transportProperties, s.acceptTimestamp, masterKey.getBytes(),
				s.remoteAuthor, s.remoteEphemeralPublicKey,
				s.remoteTransportProperties, s.remoteAcceptTimestamp,
				s.transportKeys);
	}

	static IntroduceeSession awaitActivate(IntroduceeSession s, AuthMessage m,
			Message sent, @Nullable Map<TransportId, KeySetId> transportKeys) {
		return new IntroduceeSession(s.getSessionId(), AWAIT_ACTIVATE,
				s.getRequestTimestamp(), s.contactGroupId, sent.getId(),
				sent.getTimestamp(), m.getMessageId(), s.introducer, null, null,
				null, s.acceptTimestamp, null, s.getRemoteAuthor(), null, null,
				s.remoteAcceptTimestamp, transportKeys);
	}

	static IntroduceeSession clear(IntroduceeSession s,
			@Nullable MessageId lastLocalMessageId, long localTimestamp,
			@Nullable MessageId lastRemoteMessageId) {
		return new IntroduceeSession(s.getSessionId(), START,
				s.getRequestTimestamp(), s.getContactGroupId(),
				lastLocalMessageId, localTimestamp, lastRemoteMessageId,
				s.getIntroducer(), null, null, null, -1, null,
				s.getRemoteAuthor(), null, null, -1, null);
	}

	@Override
	Role getRole() {
		return INTRODUCEE;
	}

	public GroupId getContactGroupId() {
		return contactGroupId;
	}

	public long getLocalTimestamp() {
		return localTimestamp;
	}

	@Nullable
	public MessageId getLastLocalMessageId() {
		return lastLocalMessageId;
	}

	@Nullable
	public MessageId getLastRemoteMessageId() {
		return lastRemoteMessageId;
	}

	Author getIntroducer() {
		return introducer;
	}

	@Nullable
	byte[] getEphemeralPublicKey() {
		return ephemeralPublicKey;
	}

	@Nullable
	byte[] getEphemeralPrivateKey() {
		return ephemeralPrivateKey;
	}

	@Nullable
	Map<TransportId, TransportProperties> getTransportProperties() {
		return transportProperties;
	}

	long getAcceptTimestamp() {
		return acceptTimestamp;
	}

	@Nullable
	byte[] getMasterKey() {
		return masterKey;
	}

	Author getRemoteAuthor() {
		return remoteAuthor;
	}

	@Nullable
	byte[] getRemotePublicKey() {
		return remoteEphemeralPublicKey;
	}

	@Nullable
	Map<TransportId, TransportProperties> getRemoteTransportProperties() {
		return remoteTransportProperties;
	}

	long getRemoteAcceptTimestamp() {
		return remoteAcceptTimestamp;
	}

	@Nullable
	Map<TransportId, KeySetId> getTransportKeys() {
		return transportKeys;
	}

}
