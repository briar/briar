package org.briarproject.api.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.sharing.SharingMessage.Invitation;
import org.briarproject.api.sync.GroupId;

import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.api.sharing.SharingConstants.INVITATION_MSG;
import static org.briarproject.api.sharing.SharingConstants.SESSION_ID;

public interface ForumSharingMessage {

	class ForumInvitation extends Invitation {

		private final String forumName;
		private final byte[] forumSalt;

		public ForumInvitation(GroupId groupId, SessionId sessionId,
				String forumName, byte[] forumSalt, String message) {

			super(groupId, sessionId, message);

			this.forumName = forumName;
			this.forumSalt = forumSalt;
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

		public static ForumInvitation from(GroupId groupId, BdfDictionary d)
				throws FormatException {

			SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));
			String forumName = d.getString(FORUM_NAME);
			byte[] forumSalt = d.getRaw(FORUM_SALT);
			String message = d.getOptionalString(INVITATION_MSG);

			return new ForumInvitation(groupId, sessionId, forumName, forumSalt,
					message);
		}

		public String getForumName() {
			return forumName;
		}

		public byte[] getForumSalt() {
			return forumSalt;
		}
	}
}
