package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.briar.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_SALT;

@NotThreadSafe
@NotNullByDefault
class ForumSharerSessionState extends SharerSessionState {

	private final String forumName;
	private final byte[] forumSalt;

	ForumSharerSessionState(SessionId sessionId, MessageId storageId,
			GroupId groupId, State state, ContactId contactId, GroupId forumId,
			String forumName, byte[] forumSalt,
			@Nullable MessageId responseId) {
		super(sessionId, storageId, groupId, state, contactId, forumId,
				responseId);

		this.forumName = forumName;
		this.forumSalt = forumSalt;
	}

	@Override
	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = super.toBdfDictionary();
		d.put(FORUM_NAME, getForumName());
		d.put(FORUM_SALT, getForumSalt());
		return d;
	}

	String getForumName() {
		return forumName;
	}

	byte[] getForumSalt() {
		return forumSalt;
	}
}
