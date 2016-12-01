package org.briarproject.briar.api.privategroup;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface PrivateGroupConstants {

	/**
	 * The maximum length of a group's name in UTF-8 bytes.
	 */
	int MAX_GROUP_NAME_LENGTH = 100;

	/**
	 * The length of a group's random salt in bytes.
	 */
	int GROUP_SALT_LENGTH = 32;

	/**
	 * The maximum length of a group post's body in bytes.
	 */
	int MAX_GROUP_POST_BODY_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	/**
	 * The maximum length of a group invitation message in bytes.
	 */
	int MAX_GROUP_INVITATION_MSG_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

}
