package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.Role;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.introduction.Role.INTRODUCER;

@Immutable
@NotNullByDefault
class IntroducerSession extends Session<IntroducerState> {

	private final Introducee introducee1, introducee2;

	IntroducerSession(SessionId sessionId, IntroducerState state,
			long requestTimestamp, Introducee introducee1,
			Introducee introducee2) {
		super(sessionId, state, requestTimestamp);
		this.introducee1 = introducee1;
		this.introducee2 = introducee2;
	}

	IntroducerSession(SessionId sessionId, GroupId groupId1, Author author1,
			GroupId groupId2, Author author2) {
		this(sessionId, IntroducerState.START, -1,
				new Introducee(sessionId, groupId1, author1),
				new Introducee(sessionId, groupId2, author2));
	}

	@Override
	Role getRole() {
		return INTRODUCER;
	}

	Introducee getIntroducee1() {
		return introducee1;
	}

	Introducee getIntroducee2() {
		return introducee2;
	}

	@Immutable
	@NotNullByDefault
	static class Introducee implements PeerSession {
		final SessionId sessionId;
		final GroupId groupId;
		final Author author;
		final long localTimestamp;
		@Nullable
		final MessageId lastLocalMessageId, lastRemoteMessageId;

		Introducee(SessionId sessionId, GroupId groupId, Author author,
				long localTimestamp,
				@Nullable MessageId lastLocalMessageId,
				@Nullable MessageId lastRemoteMessageId) {
			this.sessionId = sessionId;
			this.groupId = groupId;
			this.localTimestamp = localTimestamp;
			this.author = author;
			this.lastLocalMessageId = lastLocalMessageId;
			this.lastRemoteMessageId = lastRemoteMessageId;
		}

		Introducee(Introducee i, Message sent) {
			this(i.sessionId, i.groupId, i.author, sent.getTimestamp(),
					sent.getId(), i.lastRemoteMessageId);
		}

		Introducee(Introducee i, MessageId remoteMessageId) {
			this(i.sessionId, i.groupId, i.author, i.localTimestamp,
					i.lastLocalMessageId, remoteMessageId);
		}

		private Introducee(SessionId sessionId, GroupId groupId,
				Author author) {
			this(sessionId, groupId, author, -1, null, null);
		}

		public SessionId getSessionId() {
			return sessionId;
		}

		@Override
		public GroupId getContactGroupId() {
			return groupId;
		}

		@Override
		public long getLocalTimestamp() {
			return localTimestamp;
		}

		@Nullable
		@Override
		public MessageId getLastLocalMessageId() {
			return lastLocalMessageId;
		}

		@Nullable
		@Override
		public MessageId getLastRemoteMessageId() {
			return lastRemoteMessageId;
		}

	}

}
