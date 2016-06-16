package org.briarproject.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;

public class ForumInviteeSessionState extends InviteeSessionState {

	private final String forumName;
	private final byte[] forumSalt;

	public ForumInviteeSessionState(SessionId sessionId, MessageId storageId,
			GroupId groupId, State state, ContactId contactId, GroupId forumId,
			String forumName, byte[] forumSalt) {
		super(sessionId, storageId, groupId, state, contactId, forumId);

		this.forumName = forumName;
		this.forumSalt = forumSalt;
	}

	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = super.toBdfDictionary();
		d.put(FORUM_NAME, getForumName());
		d.put(FORUM_SALT, getForumSalt());
		return d;
	}

	public String getForumName() {
		return forumName;
	}

	public byte[] getForumSalt() {
		return forumSalt;
	}
}
