package org.briarproject.briar.api.forum;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface ForumConstants {

	/**
	 * The maximum length of a forum's name in UTF-8 bytes.
	 */
	int MAX_FORUM_NAME_LENGTH = 100;

	/**
	 * The length of a forum's random salt in bytes.
	 */
	int FORUM_SALT_LENGTH = 32;

	/**
	 * The maximum length of a forum post's content type in UTF-8 bytes.
	 */
	int MAX_CONTENT_TYPE_LENGTH = 50;

	/**
	 * The maximum length of a forum post's body in bytes.
	 */
	int MAX_FORUM_POST_BODY_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	/* Forum Sharing Constants */
	String FORUM_NAME = "forumName";
	String FORUM_SALT = "forumSalt";

	// Database keys
	String KEY_TIMESTAMP = "timestamp";
	String KEY_PARENT = "parent";
	String KEY_ID = "id";
	String KEY_NAME = "name";
	String KEY_PUBLIC_NAME = "publicKey";
	String KEY_AUTHOR = "author";
	String KEY_LOCAL = "local";

}
