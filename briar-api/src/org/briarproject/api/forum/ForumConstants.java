package org.briarproject.api.forum;

import static org.briarproject.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface ForumConstants {

	/** The maximum length of a forum's name in bytes. */
	int MAX_FORUM_NAME_LENGTH = MAX_GROUP_DESCRIPTOR_LENGTH - 10;

	/** The length of a forum's random salt in bytes. */
	int FORUM_SALT_LENGTH = 32;

	/** The maximum length of a forum post's content type in bytes. */
	int MAX_CONTENT_TYPE_LENGTH = 50;

	/** The maximum length of a forum post's body in bytes. */
	int MAX_FORUM_POST_BODY_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;
}
