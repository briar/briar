package org.briarproject.briar.api.sharing;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface SharingConstants {

	/**
	 * The length of a sharing session's random salt in bytes.
	 */
	int SHARING_SALT_LENGTH = 32;

	/**
	 * The maximum length of the optional message from the inviter to the
	 * invitee in UTF-8 bytes.
	 */
	int MAX_INVITATION_MESSAGE_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

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
	String IS_SHARER = "isSharer";
	String SHAREABLE_ID = "shareableId";
	String INVITATION_MSG = "invitationMsg";
	String INVITATION_ID = "invitationId";
	String RESPONSE_ID = "responseId";
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
