package org.briarproject.api.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.sync.GroupId;

import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.api.forum.ForumConstants.GROUP_ID;
import static org.briarproject.api.forum.ForumConstants.INVITATION_MSG;
import static org.briarproject.api.forum.ForumConstants.SESSION_ID;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.forum.ForumConstants.TYPE;

public interface ForumSharingMessage {

	abstract class BaseMessage {
		private final GroupId groupId;
		private final SessionId sessionId;

		public BaseMessage(GroupId groupId, SessionId sessionId) {

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

		public static BaseMessage from(GroupId groupId, BdfDictionary d)
				throws FormatException {

			long type = d.getLong(TYPE);

			if (type == SHARE_MSG_TYPE_INVITATION)
				return Invitation.from(groupId, d);
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

	class Invitation extends BaseMessage {

		private final String forumName;
		private final byte[] forumSalt;
		private final String message;

		public Invitation(GroupId groupId, SessionId sessionId,
				String forumName, byte[] forumSalt, String message) {

			super(groupId, sessionId);

			this.forumName = forumName;
			this.forumSalt = forumSalt;
			this.message = message;
		}

		@Override
		public long getType() {
			return SHARE_MSG_TYPE_INVITATION;
		}

		@Override
		public BdfList toBdfList() {
			BdfList list = super.toBdfList();
			list.add(forumName);
			list.add(forumSalt);
			if (message != null) list.add(message);
			return list;
		}

		@Override
		public BdfDictionary toBdfDictionary() {
			BdfDictionary d = toBdfDictionaryHelper();
			d.put(FORUM_NAME, forumName);
			d.put(FORUM_SALT, forumSalt);
			if (message != null) d.put(INVITATION_MSG, message);
			return d;
		}

		public static Invitation from(GroupId groupId, BdfDictionary d)
				throws FormatException {

			SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));
			String forumName = d.getString(FORUM_NAME);
			byte[] forumSalt = d.getRaw(FORUM_SALT);
			String message = d.getOptionalString(INVITATION_MSG);

			return new Invitation(groupId, sessionId, forumName, forumSalt,
					message);
		}

		public String getForumName() {
			return forumName;
		}

		public byte[] getForumSalt() {
			return forumSalt;
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
