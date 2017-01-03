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

	@Deprecated
	String CONTACT_ID = "contactId";
	@Deprecated
	String GROUP_ID = "groupId";
	@Deprecated
	String TO_BE_SHARED_BY_US = "toBeSharedByUs";
	@Deprecated
	String SHARED_BY_US = "sharedByUs";
	@Deprecated
	String SHARED_WITH_US = "sharedWithUs";
	@Deprecated
	String TYPE = "type";
	@Deprecated
	String SESSION_ID = "sessionId";
	@Deprecated
	String STORAGE_ID = "storageId";
	@Deprecated
	String STATE = "state";
	@Deprecated
	String LOCAL = "local";
	@Deprecated
	String TIME = "time";
	@Deprecated
	String IS_SHARER = "isSharer";
	@Deprecated
	String SHAREABLE_ID = "shareableId";
	@Deprecated
	String INVITATION_MSG = "invitationMsg";
	@Deprecated
	String INVITATION_ID = "invitationId";
	@Deprecated
	String RESPONSE_ID = "responseId";
	@Deprecated
	int SHARE_MSG_TYPE_INVITATION = 1;
	@Deprecated
	int SHARE_MSG_TYPE_ACCEPT = 2;
	@Deprecated
	int SHARE_MSG_TYPE_DECLINE = 3;
	@Deprecated
	int SHARE_MSG_TYPE_LEAVE = 4;
	@Deprecated
	int SHARE_MSG_TYPE_ABORT = 5;
	@Deprecated
	int TASK_ADD_SHAREABLE_TO_LIST_SHARED_WITH_US = 0;
	@Deprecated
	int TASK_REMOVE_SHAREABLE_FROM_LIST_SHARED_WITH_US = 1;
	@Deprecated
	int TASK_ADD_SHARED_SHAREABLE = 2;
	@Deprecated
	int TASK_ADD_SHAREABLE_TO_LIST_TO_BE_SHARED_BY_US = 3;
	@Deprecated
	int TASK_REMOVE_SHAREABLE_FROM_LIST_TO_BE_SHARED_BY_US = 4;
	@Deprecated
	int TASK_SHARE_SHAREABLE = 5;
	@Deprecated
	int TASK_UNSHARE_SHAREABLE_SHARED_BY_US = 6;
	@Deprecated
	int TASK_UNSHARE_SHAREABLE_SHARED_WITH_US = 7;

}
