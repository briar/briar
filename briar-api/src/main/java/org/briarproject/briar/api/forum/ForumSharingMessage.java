package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.SharingMessage.Invitation;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.briar.api.sharing.SharingConstants.INVITATION_MSG;
import static org.briarproject.briar.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.TIME;

@NotNullByDefault
public interface ForumSharingMessage {

	@Immutable
	@NotNullByDefault
	class ForumInvitation extends Invitation {

		private final String forumName;
		private final byte[] forumSalt;

		public ForumInvitation(GroupId groupId, SessionId sessionId,
				String forumName, byte[] forumSalt, long time,
				@Nullable String message) {

			super(groupId, sessionId, time, message);

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
			long time = d.getLong(TIME);

			return new ForumInvitation(groupId, sessionId, forumName, forumSalt,
					time, message);
		}

		public String getForumName() {
			return forumName;
		}

		public byte[] getForumSalt() {
			return forumSalt;
		}
	}
}
