package org.briarproject.api.sharing;

public interface SharingConstants {

	/** The length of a sharing session's random salt in bytes. */
	int SHARING_SALT_LENGTH = 32;

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
	String SHAREABLE_ID = "shareableId";
	String INVITATION_MSG = "invitationMsg";
	int SHARE_MSG_TYPE_INVITATION = 1;
	int SHARE_MSG_TYPE_ACCEPT = 2;
	int SHARE_MSG_TYPE_DECLINE = 3;
	int SHARE_MSG_TYPE_LEAVE = 4;
	int SHARE_MSG_TYPE_ABORT = 5;
	int TASK_ADD_SHAREABLE_TO_LIST_SHARED_WITH_US = 0;
	int TASK_REMOVE_SHAREABLE_FROM_LIST_SHARED_WITH_US = 1;
	int TASK_ADD_SHARED_SHAREABLE = 2;
	int TASK_ADD_SHAREABLE_TO_LIST_TO_BE_SHARED_BY_US = 3;
	int TASK_REMOVE_SHAREABLE_FROM_LIST_TO_BE_SHARED_BY_US = 4;
	int TASK_SHARE_SHAREABLE = 5;
	int TASK_UNSHARE_SHAREABLE_SHARED_BY_US = 6;
	int TASK_UNSHARE_SHAREABLE_SHARED_WITH_US = 7;

}
