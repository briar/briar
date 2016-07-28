package org.briarproject.api.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.sync.GroupId;

import static org.briarproject.api.sharing.SharingConstants.GROUP_ID;
import static org.briarproject.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.sharing.SharingConstants.TYPE;

public interface SharingMessage {

	abstract class BaseMessage {
		private final GroupId groupId;
		private final SessionId sessionId;

		BaseMessage(GroupId groupId, SessionId sessionId) {

			this.groupId = groupId;
			this.sessionId = sessionId;
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
	}

	abstract class Invitation extends BaseMessage {

		protected final String message;

		public Invitation(GroupId groupId, SessionId sessionId,
				String message) {

			super(groupId, sessionId);

			this.message = message;
		}

		@Override
		public long getType() {
			return SHARE_MSG_TYPE_INVITATION;
		}

		public String getMessage() {
			return message;
		}
	}

	class SimpleMessage extends BaseMessage {

		private final long type;

		public SimpleMessage(long type, GroupId groupId, SessionId sessionId) {
			super(groupId, sessionId);
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
			return new SimpleMessage(type, groupId, sessionId);
		}
	}

}
