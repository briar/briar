package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
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

import static org.briarproject.briar.api.introduction.Role.INTRODUCEE;
import static org.briarproject.briar.introduction.IntroduceeState.AWAIT_ACTIVATE;
import static org.briarproject.briar.introduction.IntroduceeState.START;

@Immutable
@NotNullByDefault
class IntroduceeSession extends Session<IntroduceeState>
		implements PeerSession {

	private final GroupId contactGroupId;
	private final Author introducer;
	private final Local local;
	private final Remote remote;
	@Nullable
	private final byte[] masterKey;
	@Nullable
	private final Map<TransportId, KeySetId> transportKeys;

	IntroduceeSession(SessionId sessionId, IntroduceeState state,
			long requestTimestamp, GroupId contactGroupId, Author introducer,
			Local local, Remote remote, @Nullable byte[] masterKey,
			@Nullable Map<TransportId, KeySetId> transportKeys) {
		super(sessionId, state, requestTimestamp);
		this.contactGroupId = contactGroupId;
		this.introducer = introducer;
		this.local = local;
		this.remote = remote;
		this.masterKey = masterKey;
		this.transportKeys = transportKeys;
	}

	static IntroduceeSession getInitial(GroupId contactGroupId,
			SessionId sessionId, Author introducer, boolean localIsAlice,
			Author remoteAuthor) {
		Local local =
				new Local(localIsAlice, null, -1, null, null, null, -1, null);
		Remote remote =
				new Remote(!localIsAlice, remoteAuthor, null, null, null, -1,
						null);
		return new IntroduceeSession(sessionId, START, -1, contactGroupId,
				introducer, local, remote, null, null);
	}

	static IntroduceeSession addRemoteRequest(IntroduceeSession s,
			IntroduceeState state, RequestMessage m) {
		Remote remote = new Remote(s.remote, m.getMessageId());
		return new IntroduceeSession(s.getSessionId(), state, m.getTimestamp(),
				s.contactGroupId, s.introducer, s.local, remote, s.masterKey,
				s.transportKeys);
	}

	static IntroduceeSession addLocalAccept(IntroduceeSession s,
			IntroduceeState state, Message acceptMessage,
			PublicKey ephemeralPublicKey, PrivateKey ephemeralPrivateKey,
			long acceptTimestamp,
			Map<TransportId, TransportProperties> transportProperties) {
		Local local = new Local(s.local.alice, acceptMessage.getId(),
				acceptMessage.getTimestamp(), ephemeralPublicKey,
				ephemeralPrivateKey, transportProperties, acceptTimestamp,
				null);
		return new IntroduceeSession(s.getSessionId(), state,
				s.getRequestTimestamp(), s.contactGroupId, s.introducer, local,
				s.remote, s.masterKey, s.transportKeys);
	}

	static IntroduceeSession addRemoteAccept(IntroduceeSession s,
			IntroduceeState state, AcceptMessage m) {
		Remote remote =
				new Remote(s.remote.alice, s.remote.author, m.getMessageId(),
						m.getEphemeralPublicKey(), m.getTransportProperties(),
						m.getAcceptTimestamp(), s.remote.macKey);
		return new IntroduceeSession(s.getSessionId(), state,
				s.getRequestTimestamp(), s.contactGroupId, s.introducer,
				s.local, remote, s.masterKey, s.transportKeys);
	}

	static IntroduceeSession addLocalAuth(IntroduceeSession s,
			IntroduceeState state, Message m, SecretKey masterKey,
			SecretKey aliceMacKey, SecretKey bobMacKey) {
		// add mac key and sent message
		Local local = new Local(s.local.alice, m.getId(), m.getTimestamp(),
				s.local.ephemeralPublicKey, s.local.ephemeralPrivateKey,
				s.local.transportProperties, s.local.acceptTimestamp,
				s.local.alice ? aliceMacKey.getBytes() : bobMacKey.getBytes());
		// just add the mac key
		Remote remote = new Remote(s.remote.alice, s.remote.author,
				s.remote.lastMessageId, s.remote.ephemeralPublicKey,
				s.remote.transportProperties, s.remote.acceptTimestamp,
				s.remote.alice ? aliceMacKey.getBytes() : bobMacKey.getBytes());
		// add master key
		return new IntroduceeSession(s.getSessionId(), state,
				s.getRequestTimestamp(), s.contactGroupId, s.introducer, local,
				remote, masterKey.getBytes(), s.transportKeys);
	}

	static IntroduceeSession awaitActivate(IntroduceeSession s, AuthMessage m,
			Message sent, @Nullable Map<TransportId, KeySetId> transportKeys) {
		Local local = Local.clear(s.local, sent.getId(), sent.getTimestamp());
		Remote remote = Remote.clear(s.remote, m.getMessageId());
		return new IntroduceeSession(s.getSessionId(), AWAIT_ACTIVATE,
				s.getRequestTimestamp(), s.contactGroupId, s.introducer, local,
				remote, null, transportKeys);
	}

	static IntroduceeSession clear(IntroduceeSession s, IntroduceeState state,
			@Nullable MessageId lastLocalMessageId, long localTimestamp,
			@Nullable MessageId lastRemoteMessageId) {
		Local local =
				new Local(s.local.alice, lastLocalMessageId, localTimestamp,
						null, null, null, -1, null);
		Remote remote =
				new Remote(s.remote.alice, s.remote.author, lastRemoteMessageId,
						null, null, -1, null);
		return new IntroduceeSession(s.getSessionId(), state,
				s.getRequestTimestamp(), s.contactGroupId, s.introducer, local,
				remote, null, null);
	}

	@Override
	Role getRole() {
		return INTRODUCEE;
	}

	@Override
	public GroupId getContactGroupId() {
		return contactGroupId;
	}

	@Override
	public long getLocalTimestamp() {
		return local.lastMessageTimestamp;
	}

	@Nullable
	@Override
	public MessageId getLastLocalMessageId() {
		return local.lastMessageId;
	}

	@Nullable
	@Override
	public MessageId getLastRemoteMessageId() {
		return remote.lastMessageId;
	}

	Author getIntroducer() {
		return introducer;
	}

	public Local getLocal() {
		return local;
	}

	public Remote getRemote() {
		return remote;
	}

	@Nullable
	byte[] getMasterKey() {
		return masterKey;
	}

	@Nullable
	Map<TransportId, KeySetId> getTransportKeys() {
		return transportKeys;
	}

	abstract static class Common {
		final boolean alice;
		@Nullable
		final MessageId lastMessageId;
		@Nullable
		final PublicKey ephemeralPublicKey;
		@Nullable
		final Map<TransportId, TransportProperties> transportProperties;
		final long acceptTimestamp;
		@Nullable
		final byte[] macKey;

		private Common(boolean alice, @Nullable MessageId lastMessageId,
				@Nullable PublicKey ephemeralPublicKey, @Nullable
				Map<TransportId, TransportProperties> transportProperties,
				long acceptTimestamp, @Nullable byte[] macKey) {
			this.alice = alice;
			this.lastMessageId = lastMessageId;
			this.ephemeralPublicKey = ephemeralPublicKey;
			this.transportProperties = transportProperties;
			this.acceptTimestamp = acceptTimestamp;
			this.macKey = macKey;
		}
	}

	static class Local extends Common {
		final long lastMessageTimestamp;
		@Nullable
		final PrivateKey ephemeralPrivateKey;

		Local(boolean alice, @Nullable MessageId lastMessageId,
				long lastMessageTimestamp,
				@Nullable PublicKey ephemeralPublicKey,
				@Nullable PrivateKey ephemeralPrivateKey, @Nullable
				Map<TransportId, TransportProperties> transportProperties,
				long acceptTimestamp, @Nullable byte[] macKey) {
			super(alice, lastMessageId, ephemeralPublicKey, transportProperties,
					acceptTimestamp, macKey);
			this.lastMessageTimestamp = lastMessageTimestamp;
			this.ephemeralPrivateKey = ephemeralPrivateKey;
		}

		/**
		 * Returns a copy of the given Local, updating the last message ID
		 * and timestamp and clearing the ephemeral keys.
		 */
		private static Local clear(Local s,
				@Nullable MessageId lastMessageId, long lastMessageTimestamp) {
			return new Local(s.alice, lastMessageId, lastMessageTimestamp,
					null, null, s.transportProperties, s.acceptTimestamp,
					s.macKey);
		}
	}

	static class Remote extends Common {
		final Author author;

		Remote(boolean alice, Author author,
				@Nullable MessageId lastMessageId,
				@Nullable PublicKey ephemeralPublicKey, @Nullable
				Map<TransportId, TransportProperties> transportProperties,
				long acceptTimestamp, @Nullable byte[] macKey) {
			super(alice, lastMessageId, ephemeralPublicKey, transportProperties,
					acceptTimestamp, macKey);
			this.author = author;
		}

		/**
		 * Returns a copy of the given Remote, updating the last message ID.
		 */
		private Remote(Remote s, @Nullable MessageId lastMessageId) {
			this(s.alice, s.author, lastMessageId, s.ephemeralPublicKey,
					s.transportProperties, s.acceptTimestamp, s.macKey);
		}

		/**
		 * Returns a copy of the given Remote, updating the last message ID
		 * and clearing the ephemeral keys.
		 */
		private static Remote clear(Remote s,
				@Nullable MessageId lastMessageId) {
			return new Remote(s.alice, s.author, lastMessageId, null,
					s.transportProperties, s.acceptTimestamp, s.macKey);
		}
	}

}
