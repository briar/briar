package org.briarproject.api.forum;

import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;

public enum InviteeAction {

	LOCAL_ACCEPT,
	LOCAL_DECLINE,
	LOCAL_LEAVE,
	LOCAL_ABORT,
	REMOTE_INVITATION,
	REMOTE_LEAVE,
	REMOTE_ABORT;

	public static InviteeAction getLocal(long type) {
		if (type == SHARE_MSG_TYPE_ACCEPT) return LOCAL_ACCEPT;
		if (type == SHARE_MSG_TYPE_DECLINE) return LOCAL_DECLINE;
		if (type == SHARE_MSG_TYPE_LEAVE) return LOCAL_LEAVE;
		if (type == SHARE_MSG_TYPE_ABORT) return LOCAL_ABORT;
		return null;
	}

	public static InviteeAction getRemote(long type) {
		if (type == SHARE_MSG_TYPE_INVITATION) return REMOTE_INVITATION;
		if (type == SHARE_MSG_TYPE_LEAVE) return REMOTE_LEAVE;
		if (type == SHARE_MSG_TYPE_ABORT) return REMOTE_ABORT;
		return null;
	}

}
