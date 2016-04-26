package org.briarproject.api.forum;

import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface ForumConstants {

	/** The maximum length of a forum's name in UTF-8 bytes. */
	int MAX_FORUM_NAME_LENGTH = 100;

	/** The length of a forum's random salt in bytes. */
	int FORUM_SALT_LENGTH = 32;

	/** The maximum length of a forum post's content type in UTF-8 bytes. */
	int MAX_CONTENT_TYPE_LENGTH = 50;

	/** The maximum length of a forum post's body in bytes. */
	int MAX_FORUM_POST_BODY_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	/* Forum Sharing Constants */
	String CONTACT_ID = "contactId";
	String GROUP_ID = "groupId";
	String TO_BE_SHARED_BY_US = "toBeSharedByUs";
	String SHARED_BY_US = "sharedByUs";
	String SHARED_WITH_US = "sharedWithUs";
	String TYPE = "type";
	String SESSION_ID = "sessionId";
	String STORAGE_ID = "storageId";
	String STATE = "state";
	String LOCAL = "local";
	String TIME = "time";
	String READ = "read";
	String IS_SHARER = "isSharer";
	String FORUM_ID = "forumId";
	String FORUM_NAME = "forumName";
	String FORUM_SALT = "forumSalt";
	String INVITATION_MSG = "invitationMsg";
	int SHARE_MSG_TYPE_INVITATION = 1;
	int SHARE_MSG_TYPE_ACCEPT = 2;
	int SHARE_MSG_TYPE_DECLINE = 3;
	int SHARE_MSG_TYPE_LEAVE = 4;
	int SHARE_MSG_TYPE_ABORT = 5;
	String TASK = "task";
	int TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US = 0;
	int TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US = 1;
	int TASK_ADD_SHARED_FORUM = 2;
	int TASK_ADD_FORUM_TO_LIST_TO_BE_SHARED_BY_US = 3;
	int TASK_REMOVE_FORUM_FROM_LIST_TO_BE_SHARED_BY_US = 4;
	int TASK_SHARE_FORUM = 5;
	int TASK_UNSHARE_FORUM_SHARED_BY_US = 6;
	int TASK_UNSHARE_FORUM_SHARED_WITH_US = 7;

}
