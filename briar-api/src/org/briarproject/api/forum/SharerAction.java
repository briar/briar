package org.briarproject.api.forum;

import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;

public enum SharerAction {

	LOCAL_INVITATION,
	LOCAL_LEAVE,
	LOCAL_ABORT,
	REMOTE_ACCEPT,
	REMOTE_DECLINE,
	REMOTE_LEAVE,
	REMOTE_ABORT;

	public static SharerAction getLocal(long type) {
		if (type == SHARE_MSG_TYPE_INVITATION) return LOCAL_INVITATION;
		if (type == SHARE_MSG_TYPE_LEAVE) return LOCAL_LEAVE;
		if (type == SHARE_MSG_TYPE_ABORT) return LOCAL_ABORT;
		return null;
	}

	public static SharerAction getRemote(long type) {
		if (type == SHARE_MSG_TYPE_ACCEPT) return REMOTE_ACCEPT;
		if (type == SHARE_MSG_TYPE_DECLINE) return REMOTE_DECLINE;
		if (type == SHARE_MSG_TYPE_LEAVE) return REMOTE_LEAVE;
		if (type == SHARE_MSG_TYPE_ABORT) return REMOTE_ABORT;
		return null;
	}

}
