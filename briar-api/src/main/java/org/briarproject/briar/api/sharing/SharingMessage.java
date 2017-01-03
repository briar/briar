package org.briarproject.briar.api.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.sharing.SharingConstants.GROUP_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.briar.api.sharing.SharingConstants.TIME;
import static org.briarproject.briar.api.sharing.SharingConstants.TYPE;

@Deprecated
@NotNullByDefault
public interface SharingMessage {

	@Immutable
	@NotNullByDefault
	abstract class BaseMessage {

		private final GroupId groupId;
		private final SessionId sessionId;
		private final long time;

		BaseMessage(GroupId groupId, SessionId sessionId, long time) {
			this.groupId = groupId;
			this.sessionId = sessionId;
			this.time = time;
		}

		public BdfList toBdfList() {
			return BdfList.of(getType(), getSessionId());
		}

		public abstract BdfDictionary toBdfDictionary();

		protected BdfDictionary toBdfDictionaryHelper() {
			return BdfDictionary.of(
					new BdfEntry(TYPE, getType()),
					new BdfEntry(GROUP_ID, groupId),
					new BdfEntry(SESSION_ID, sessionId)
			);
		}

		public static BaseMessage from(InvitationFactory invitationFactory,
				GroupId groupId, BdfDictionary d)
				throws FormatException {

			long type = d.getLong(TYPE);

			if (type == SHARE_MSG_TYPE_INVITATION)
				return invitationFactory.build(groupId, d);
			else
				return SimpleMessage.from(type, groupId, d);
		}

		public abstract long getType();

		public GroupId getGroupId() {
			return groupId;
		}

		public SessionId getSessionId() {
			return sessionId;
		}

		public long getTime() {
			return time;
		}
	}

	@Immutable
	@NotNullByDefault
	abstract class Invitation extends BaseMessage {

		@Nullable
		protected final String message;

		public Invitation(GroupId groupId, SessionId sessionId, long time,
				@Nullable String message) {

			super(groupId, sessionId, time);

			this.message = message;
		}

		@Override
		public long getType() {
			return SHARE_MSG_TYPE_INVITATION;
		}

		@Nullable
		public String getMessage() {
			return message;
		}
	}

	@Immutable
	@NotNullByDefault
	class SimpleMessage extends BaseMessage {

		private final long type;

		public SimpleMessage(long type, GroupId groupId, SessionId sessionId,
				long time) {
			super(groupId, sessionId, time);
			this.type = type;
		}

		@Override
		public long getType() {
			return type;
		}

		@Override
		public BdfDictionary toBdfDictionary() {
			return toBdfDictionaryHelper();
		}

		public static SimpleMessage from(long type, GroupId groupId,
				BdfDictionary d) throws FormatException {

			if (type != SHARE_MSG_TYPE_ACCEPT &&
					type != SHARE_MSG_TYPE_DECLINE &&
					type != SHARE_MSG_TYPE_LEAVE &&
					type != SHARE_MSG_TYPE_ABORT) throw new FormatException();

			SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));
			long time = d.getLong(TIME);
			return new SimpleMessage(type, groupId, sessionId, time);
		}
	}

}
